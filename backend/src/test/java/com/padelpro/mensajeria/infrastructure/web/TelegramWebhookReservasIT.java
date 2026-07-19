package com.padelpro.mensajeria.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.EncryptionService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook integration test for the Telegram reservations dispatcher (task 6.1, capability
 * bot-telegram-reservas). Drives the real HTTP entry point {@code POST /api/bot/telegram} against a
 * real PostgreSQL (via {@link PostgresIntegrationTest}): an incoming Telegram update JSON is
 * dispatched and the resulting effect on the database is asserted, exercising the full
 * {@code /reservar → /confirmar → /cancelar} journey plus the security boundaries (invalid secret,
 * unlinked chat, someone else's reservation).
 *
 * <p><b>Why the OTP flows through a spy:</b> the confirmation/cancellation codes are single-use and
 * never persisted in clear (only their SHA-256 digest lives in {@code otp_codes} — RN-RGPD-04), so
 * the test cannot read them from the database. Instead it spies on the outbound {@link TelegramPort}
 * and extracts the 6-digit code from the exact message the bot replied to the user — the same code
 * the user would receive in Telegram. The real adapter degrades to a no-op with no bot token
 * configured, so no external HTTP call is made.
 *
 * <p><b>Local vs CI (known limitation):</b> like every {@code *IT} in this repo, this needs a real
 * PostgreSQL. It runs green in CI (Linux Docker) and via the external "Vía A" datasource; it does not
 * run under the broken Docker Desktop on the Windows dev host (TESTING-STRATEGY §3.3).
 */
@DisplayName("IT — webhook Telegram reservas: /reservar → /confirmar → /cancelar + seguridad")
class TelegramWebhookReservasIT extends PostgresIntegrationTest {

    private static final String SECRET = "webhook-secret-it";
    private static final String OWNER_CHAT = "700100100";
    private static final String OTHER_CHAT = "700200200";
    private static final String UNLINKED_CHAT = "700999999";
    // Anchor on the code position (it always follows "/confirmar <ref> ") so the 8-hex short
    // reference — which can contain its own 6-digit run — is never mistaken for the OTP.
    private static final Pattern CONFIRM_CODE = Pattern.compile("/confirmar\\s+\\S+\\s+(\\d{6})(?!\\d)");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationJpaRepository reservationRepository;
    @Autowired private EncryptionService encryptionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @SpyBean private TelegramPort telegramPort;

    private final LocalDate futureDate = LocalDate.now().plusDays(3);

    @BeforeEach
    void configureWebhookSecret() {
        // cleanup.sql (@Sql BEFORE_TEST_METHOD) does not touch telegram_webhook_secret; set it
        // deterministically each test to the AES-encrypted value TelegramConfigService will decrypt.
        jdbcTemplate.update("UPDATE system_config SET telegram_webhook_secret = ? WHERE id = 1",
                encryptionService.encrypt(SECRET));
    }

    // -------------------------------------------------------------------------
    // Happy path — full journey through the webhook
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("6.1 journey completo: reservar (PENDING) → confirmar (CONFIRMED) → cancelar (CANCELLED)")
    void full_reservar_confirmar_cancelar_journey() throws Exception {
        Long ownerId = createLinkedUser("owner", OWNER_CHAT).getId();

        // 1) /reservar → a PENDING_CONFIRMATION reservation is persisted and a confirmation code is issued.
        List<String> createReplies = dispatch(1001L, OWNER_CHAT,
                "/reservar " + futureDate + " 18:00 90");
        Reservation reservation = onlyReservationOf(ownerId);
        assertThat(reservation.getStatus().name()).isEqualTo("PENDING_CONFIRMATION");
        String ref = shortRef(reservation);
        String confirmCode = extractCode(createReplies);
        assertThat(joined(createReplies)).contains(ref);

        // 2) /confirmar <ref> <code> → the reservation transitions to CONFIRMED.
        List<String> confirmReplies = dispatch(1002L, OWNER_CHAT, "/confirmar " + ref + " " + confirmCode);
        assertThat(joined(confirmReplies)).containsIgnoringCase("confirmada");
        assertThat(reload(reservation).getStatus().name()).isEqualTo("CONFIRMED");

        // 3) /cancelar <ref> → a fresh cancellation code is issued (no state change yet).
        List<String> cancelReplies = dispatch(1003L, OWNER_CHAT, "/cancelar " + ref);
        String cancelCode = extractCode(cancelReplies);
        assertThat(reload(reservation).getStatus().name()).isEqualTo("CONFIRMED");

        // 4) /confirmar <ref> <cancelCode> → the reservation is CANCELLED (D-4: op derived from the
        //    OTP bound to the reservation, here CANCELLATION_CONFIRM).
        List<String> applied = dispatch(1004L, OWNER_CHAT, "/confirmar " + ref + " " + cancelCode);
        assertThat(joined(applied)).containsIgnoringCase("cancelada");
        assertThat(reload(reservation).getStatus().name()).isEqualTo("CANCELLED");
    }

    // -------------------------------------------------------------------------
    // Security — invalid secret (RN-TEL-01)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("6.1 secret inválido → 403 y ningún efecto (no se dispara handler)")
    void invalid_secret_is_rejected_with_403() throws Exception {
        createLinkedUser("owner", OWNER_CHAT);

        mockMvc.perform(post("/api/bot/telegram")
                        .header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(2001L, OWNER_CHAT, "/reservar " + futureDate + " 18:00 90")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bot/telegram")   // missing header entirely
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(2002L, OWNER_CHAT, "/reservar " + futureDate + " 19:00 90")))
                .andExpect(status().isForbidden());

        assertThat(reservationRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Security — unlinked chat cannot run a business operation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("6.1 chat no vinculado → 'vincula primero' y ninguna reserva creada")
    void unlinked_chat_cannot_create_reservation() throws Exception {
        // No user owns UNLINKED_CHAT.
        List<String> replies = dispatch(3001L, UNLINKED_CHAT, "/reservar " + futureDate + " 18:00 90");

        assertThat(joined(replies)).containsIgnoringCase("vincula primero");
        assertThat(reservationRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Security — a user cannot act on someone else's reservation (BOLA / RN-AUTH-02)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("6.1 reserva ajena → rechazo y la reserva del titular no cambia")
    void cannot_cancel_another_users_reservation() throws Exception {
        Long ownerId = createLinkedUser("owner", OWNER_CHAT).getId();
        createLinkedUser("intruder", OTHER_CHAT);

        // Owner creates a reservation.
        dispatch(4001L, OWNER_CHAT, "/reservar " + futureDate + " 18:00 90");
        Reservation reservation = onlyReservationOf(ownerId);
        String ref = shortRef(reservation);

        // The intruder (a linked, different account) tries to cancel it by its short reference.
        List<String> replies = dispatch(4002L, OTHER_CHAT, "/cancelar " + ref);

        // BOLA: the intruder is told it does not exist among *their* reservations; the owner's
        // reservation is untouched (no 500, no cross-account leakage).
        assertThat(joined(replies)).containsIgnoringCase("no encuentro esa reserva");
        assertThat(reload(reservation).getStatus().name()).isEqualTo("PENDING_CONFIRMATION");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Create an ACTIVE user linked to {@code chatId} (so the webhook resolves it via findByTelegramChatId). */
    private User createLinkedUser(String login, String chatId) {
        OffsetDateTime now = OffsetDateTime.now();
        User u = new User(login, "$2a$12$abcdefghijklmnopqrstuvREPLACEDbcrypthashvalueXXXXXXX",
                "First", "Last", login + "@padelpro.local",
                UserRole.USER, UserStatus.ACTIVE, now, now);
        u.setTelegramChatId(chatId);
        u.setTelegramLinkedAt(now);
        return userRepository.saveAndFlush(u);
    }

    /**
     * POST a Telegram update to the webhook (valid secret), assert 200, and return every message the
     * bot sent back to {@code chatId} during this dispatch (captured on the outbound {@link TelegramPort}).
     */
    private List<String> dispatch(long updateId, String chatId, String text) throws Exception {
        clearInvocations(telegramPort);
        mockMvc.perform(post("/api/bot/telegram")
                        .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(updateId, chatId, text)))
                .andExpect(status().isOk());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramPort, atLeastOnce()).enviarMensaje(eq(chatId), captor.capture());
        return captor.getAllValues();
    }

    /** Build a minimal Telegram update JSON: {@code update_id} + {@code message.chat.id} + {@code message.text}. */
    private String update(long updateId, String chatId, String text) throws Exception {
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("id", Long.parseLong(chatId));
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("chat", chat);
        message.put("text", text);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("update_id", updateId);
        root.put("message", message);
        return objectMapper.writeValueAsString(root);
    }

    /** The single reservation owned by {@code ownerId} (fails if there is not exactly one). */
    private Reservation onlyReservationOf(Long ownerId) {
        List<Reservation> owned = reservationRepository.findAll().stream()
                .filter(r -> ownerId.equals(r.getOwnerId()))
                .toList();
        assertThat(owned).as("exactly one reservation for owner %s", ownerId).hasSize(1);
        return owned.get(0);
    }

    private Reservation reload(Reservation reservation) {
        return reservationRepository.findById(reservation.getId()).orElseThrow();
    }

    /** Short reference the bot uses in chat: first 8 hex chars of the reservation UUID (D-2). */
    private static String shortRef(Reservation reservation) {
        return reservation.getId().toString().replace("-", "").substring(0, 8);
    }

    /** Extract the 6-digit OTP the bot embedded in one of its replies. */
    private static String extractCode(List<String> replies) {
        for (String reply : replies) {
            Matcher m = CONFIRM_CODE.matcher(reply);
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new AssertionError("No 6-digit OTP found in bot replies: " + replies);
    }

    private static String joined(List<String> replies) {
        return String.join("\n---\n", replies);
    }
}
