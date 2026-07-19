package com.padelpro.mensajeria.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.mensajeria.domain.audit.TelegramAuditActions;
import com.padelpro.mensajeria.domain.exception.TelegramWebhookForbiddenException;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import com.padelpro.otp.application.dto.GeneratedOtp;
import com.padelpro.otp.application.service.OtpService;
import com.padelpro.otp.domain.exception.OtpVerificationException;
import com.padelpro.otp.domain.model.OtpCode;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.application.service.CancelarReservaService;
import com.padelpro.reservas.application.service.ConfirmarReservaService;
import com.padelpro.reservas.application.service.CrearReservaService;
import com.padelpro.reservas.application.service.ReservaQueryService;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.exception.ReservaForbiddenException;
import com.padelpro.reservas.domain.exception.ReservaNotFoundException;
import com.padelpro.reservas.domain.exception.SlotConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Processes Telegram Bot webhook updates and dispatches slash commands (capabilities
 * auth-otp-telegram + bot-telegram-reservas).
 *
 * <p>Flow:
 * <ol>
 *   <li>Validate {@code X-Telegram-Bot-Api-Secret-Token} against the configured secret (RN-TEL-01)
 *       <em>before</em> any parsing; a missing/incorrect secret is audited and rejected with 403.</li>
 *   <li>Parse the update and route the first token to a command handler: {@code /vincular} (account
 *       linking), {@code /reservar}, {@code /misreservas}, {@code /cancelar}, {@code /confirmar} and
 *       {@code /ayuda}. Every business command resolves the account by {@code telegram_chat_id}
 *       ({@link UserRepositoryPort#findByTelegramChatId}); an unlinked chat gets "link first" and no
 *       business operation runs (spec Requirement "solo cuentas vinculadas").</li>
 * </ol>
 *
 * <p><b>Transactions (design D-3):</b> this service is a conversational adapter — an orchestrator,
 * not a transaction boundary. It is intentionally <em>not</em> {@code @Transactional}: each collaborator
 * ({@link OtpService}, {@link CrearReservaService}, {@link CancelarReservaService},
 * {@link ConfirmarReservaService}) owns its own transaction, so a business rejection can be caught here
 * to produce a friendly reply without dooming a surrounding transaction to {@code rollback-only}.
 *
 * <p>The clear OTP is never logged (RN-RGPD-04): it is only ever passed to {@link TelegramPort}; audit
 * records the action and reason, never the code.
 */
public class TelegramWebhookService {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookService.class);

    private static final Pattern VINCULAR_CODE = Pattern.compile("^\\d{6}$");
    private static final Pattern DATE_TOKEN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern TIME_TOKEN = Pattern.compile("^\\d{1,2}:\\d{2}$");
    private static final Pattern DURATION_TOKEN = Pattern.compile("^\\d{1,3}$");

    /** Default slot length (minutes) when {@code /reservar} omits the duration (Open Question resolved). */
    private static final int DEFAULT_DURATION_MINUTES = 90;

    private static final String HELP_TEXT = """
            Comandos disponibles de PadelPro:
            • /reservar <fecha> <hora> [duración] [@jugador...] — crea una reserva. Ej: /reservar 2026-08-01 18:00 90 @juan
            • /misreservas — lista tus reservas con su referencia
            • /confirmar <ref> <código> — confirma una reserva o una cancelación con el código recibido
            • /cancelar <ref> — inicia la cancelación de una reserva (recibirás un código)
            • /ayuda — muestra esta ayuda
            La fecha usa formato AAAA-MM-DD y la hora HH:mm. La duración (min) es opcional (por defecto 90).""";

    private final TelegramConfigService configService;
    private final OtpService otpService;
    private final UserRepositoryPort userRepositoryPort;
    private final TelegramAuditRecorder auditRecorder;
    private final TelegramPort telegramPort;
    private final ObjectMapper objectMapper;
    private final String profileUrl;

    private final CrearReservaService crearReservaService;
    private final ConfirmarReservaService confirmarReservaService;
    private final CancelarReservaService cancelarReservaService;
    private final ReservaQueryService reservaQueryService;

    public TelegramWebhookService(TelegramConfigService configService,
                                  OtpService otpService,
                                  UserRepositoryPort userRepositoryPort,
                                  TelegramAuditRecorder auditRecorder,
                                  TelegramPort telegramPort,
                                  ObjectMapper objectMapper,
                                  String profileUrl,
                                  CrearReservaService crearReservaService,
                                  ConfirmarReservaService confirmarReservaService,
                                  CancelarReservaService cancelarReservaService,
                                  ReservaQueryService reservaQueryService) {
        this.configService = configService;
        this.otpService = otpService;
        this.userRepositoryPort = userRepositoryPort;
        this.auditRecorder = auditRecorder;
        this.telegramPort = telegramPort;
        this.objectMapper = objectMapper;
        this.profileUrl = profileUrl;
        this.crearReservaService = crearReservaService;
        this.confirmarReservaService = confirmarReservaService;
        this.cancelarReservaService = cancelarReservaService;
        this.reservaQueryService = reservaQueryService;
    }

    /**
     * Validate the secret header and process the raw update body.
     *
     * @throws TelegramWebhookForbiddenException (→ 403) when the secret is missing/incorrect
     */
    public void handleUpdate(String secretHeader, String rawBody) {
        validateSecret(secretHeader);
        ParsedUpdate update = parse(rawBody);
        if (update == null) {
            return;
        }
        dispatch(update);
    }

    // -------------------------------------------------------------------------
    // Secret validation (RN-TEL-01) — always before parsing
    // -------------------------------------------------------------------------

    private void validateSecret(String secretHeader) {
        Optional<String> expected = configService.getWebhookSecret();
        if (secretHeader == null || expected.isEmpty() || !constantTimeEquals(secretHeader, expected.get())) {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_WEBHOOK_INVALID_SECRET, null,
                    "reason=" + (secretHeader == null ? "missing_header"
                            : expected.isEmpty() ? "no_secret_configured" : "mismatch"));
            throw new TelegramWebhookForbiddenException();
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Parsing + routing
    // -------------------------------------------------------------------------

    private ParsedUpdate parse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("Telegram webhook: unparseable update body ({})", e.getClass().getSimpleName());
            return null;
        }
        JsonNode message = root.path("message");
        String chatId = message.path("chat").path("id").asText(null);
        if (chatId == null || chatId.isBlank()) {
            return null;
        }
        String text = message.path("text").asText("");
        // Idempotency source (Open Question D): the Telegram update_id uniquely identifies a delivery,
        // so a webhook retry of the same update reuses the same key and never double-creates (D2 of reservas).
        String updateId = root.path("update_id").isMissingNode() ? null : root.path("update_id").asText(null);
        return new ParsedUpdate(chatId, text == null ? "" : text.trim(), updateId);
    }

    private void dispatch(ParsedUpdate update) {
        String command = commandToken(update.text());
        switch (command) {
            case "/vincular"    -> handleVincular(update);
            case "/reservar"    -> handleReservar(update);
            case "/misreservas" -> handleMisReservas(update);
            case "/cancelar"    -> handleCancelar(update);
            case "/confirmar"   -> handleConfirmar(update);
            case "/ayuda"       -> telegramPort.enviarMensaje(update.chatId(), HELP_TEXT);
            default             -> handleUnknown(update);
        }
    }

    /** First whitespace-delimited token, lowercased and stripped of a {@code @botname} suffix. */
    private static String commandToken(String text) {
        if (text.isEmpty()) {
            return "";
        }
        String first = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int at = first.indexOf('@');
        return at >= 0 ? first.substring(0, at) : first;
    }

    /**
     * Anything that is not a known command. A linked account gets the help text (spec: unknown command
     * → help); an unlinked chat gets "link first" (preserving the pre-existing behaviour). A plain
     * (non-slash) message from a linked account triggers no reply and no business operation.
     */
    private void handleUnknown(ParsedUpdate update) {
        Optional<User> linked = userRepositoryPort.findByTelegramChatId(update.chatId());
        if (linked.isEmpty()) {
            telegramPort.enviarMensaje(update.chatId(), "Vincula primero tu cuenta en " + profileUrl);
            return;
        }
        if (update.text().startsWith("/")) {
            telegramPort.enviarMensaje(update.chatId(), HELP_TEXT);
        }
        // A linked account sending a non-command message triggers no business operation in this scope.
    }

    // -------------------------------------------------------------------------
    // /vincular (auth-otp-telegram, spec Requirement 1) — unchanged behaviour
    // -------------------------------------------------------------------------

    private void handleVincular(ParsedUpdate update) {
        String chatId = update.chatId();
        String[] tokens = update.text().split("\\s+");
        if (tokens.length < 2 || !VINCULAR_CODE.matcher(tokens[1]).matches()) {
            telegramPort.enviarMensaje(chatId, "Formato: /vincular <código de 6 dígitos>");
            return;
        }
        String code = tokens[1];

        OtpCode otp;
        try {
            otp = otpService.resolveActiveLinkOtp(code);
        } catch (OtpVerificationException ex) {
            if ("OTP_EXPIRED".equals(ex.getCode())) {
                telegramPort.enviarMensaje(chatId, "El código ha expirado. Solicita uno nuevo desde tu perfil.");
            } else {
                telegramPort.enviarMensaje(chatId, "El código es inválido. Revisa el código o solicita uno nuevo desde tu perfil.");
            }
            return;
        }

        // Guard: the chat must not already belong to a different account (spec Req 1, scenario 3).
        Optional<User> chatOwner = userRepositoryPort.findByTelegramChatId(chatId);
        if (chatOwner.isPresent() && !chatOwner.get().getId().equals(otp.getUserId())) {
            telegramPort.enviarMensaje(chatId,
                    "Este Telegram ya está vinculado a otra cuenta. Contacta con el administrador.");
            return;
        }

        User user = userRepositoryPort.findById(otp.getUserId()).orElse(null);
        if (user == null) {
            telegramPort.enviarMensaje(chatId, "No se ha podido completar la vinculación. Inténtalo de nuevo.");
            return;
        }

        user.setTelegramChatId(chatId);
        user.setTelegramLinkedAt(OffsetDateTime.now());
        userRepositoryPort.save(user);
        otpService.consume(otp);

        auditRecorder.record(TelegramAuditActions.TELEGRAM_LINKED, user.getId(), "channel=TELEGRAM");
        telegramPort.enviarMensaje(chatId, "Tu cuenta de PadelPro ha quedado vinculada correctamente.");
    }

    // -------------------------------------------------------------------------
    // /misreservas (bot-telegram-reservas, spec "Listar mis reservas")
    // -------------------------------------------------------------------------

    private void handleMisReservas(ParsedUpdate update) {
        User user = requireLinked(update);
        if (user == null) {
            return;
        }
        List<ReservaResponse> reservas = reservaQueryService.listForUser(user.getId());
        if (reservas.isEmpty()) {
            telegramPort.enviarMensaje(update.chatId(),
                    "No tienes reservas. Crea una con /reservar <fecha> <hora>. Ej: /reservar 2026-08-01 18:00");
            return;
        }
        StringBuilder sb = new StringBuilder("Tus reservas:\n");
        for (ReservaResponse r : reservas) {
            sb.append("• ").append(shortRef(r.id()))
              .append(" — ").append(r.reservationDate()).append(' ').append(r.startTime())
              .append(" (").append(r.durationMinutes()).append(" min) — ").append(estado(r.status()))
              .append('\n');
        }
        sb.append("Usa la referencia con /confirmar o /cancelar.");
        telegramPort.enviarMensaje(update.chatId(), sb.toString().trim());
    }

    // -------------------------------------------------------------------------
    // /reservar (bot-telegram-reservas, spec "Crear reserva")
    // -------------------------------------------------------------------------

    private void handleReservar(ParsedUpdate update) {
        User user = requireLinked(update);
        if (user == null) {
            return;
        }

        CrearReservaRequest request;
        try {
            request = parseReservar(update.text());
        } catch (CommandFormatException ex) {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED, user.getId(),
                    "command=/reservar,reason=format");
            telegramPort.enviarMensaje(update.chatId(), ex.getMessage());
            return;
        } catch (ParticipantResolutionException ex) {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED, user.getId(),
                    "command=/reservar,reason=unknown_participant");
            telegramPort.enviarMensaje(update.chatId(), ex.getMessage());
            return;
        }

        String idempotencyKey = update.updateId() == null ? null : "tg-" + update.updateId();

        ReservaResponse created;
        try {
            created = crearReservaService.crear(user.getId(), request, idempotencyKey);
        } catch (SlotConflictException | InvalidReservaStateException | ValidationException | ReservaForbiddenException ex) {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED, user.getId(),
                    "command=/reservar,reason=" + ex.getClass().getSimpleName());
            telegramPort.enviarMensaje(update.chatId(), "No se pudo crear la reserva: " + ex.getMessage());
            return;
        }

        // Emit the confirmation OTP so the reservation can be confirmed with /confirmar (D-4).
        GeneratedOtp otp = otpService.generate(user.getId(), OtpType.RESERVATION_CONFIRM);
        auditRecorder.record(TelegramAuditActions.TELEGRAM_RESERVA_CREATED, user.getId(),
                "reservationId=" + created.id());

        String ref = shortRef(created.id());
        telegramPort.enviarMensaje(update.chatId(),
                "Reserva creada (" + estado(created.status()) + "). Referencia: " + ref
                        + "\nPara confirmarla envía: /confirmar " + ref + " " + otp.code()
                        + "\n(El código caduca en 10 minutos.)");
    }

    // -------------------------------------------------------------------------
    // /cancelar (bot-telegram-reservas, spec "Cancelar reserva") — step 1: emit OTP (D-4)
    // -------------------------------------------------------------------------

    private void handleCancelar(ParsedUpdate update) {
        User user = requireLinked(update);
        if (user == null) {
            return;
        }
        String[] tokens = update.text().split("\\s+");
        if (tokens.length < 2) {
            telegramPort.enviarMensaje(update.chatId(),
                    "Formato: /cancelar <referencia>. Consulta tus referencias con /misreservas.");
            return;
        }

        ReservaResponse reserva = resolveOwnedReservation(user.getId(), tokens[1]);
        if (reserva == null) {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED, user.getId(),
                    "command=/cancelar,reason=not_found_or_not_owned");
            telegramPort.enviarMensaje(update.chatId(),
                    "No encuentro esa reserva entre las tuyas. Revisa la referencia con /misreservas.");
            return;
        }

        GeneratedOtp otp = otpService.generate(user.getId(), OtpType.CANCELLATION_CONFIRM);
        String ref = shortRef(reserva.id());
        telegramPort.enviarMensaje(update.chatId(),
                "Para confirmar la cancelación de la reserva " + ref + " envía: /confirmar " + ref + " " + otp.code()
                        + "\n(El código caduca en 10 minutos.)");
    }

    // -------------------------------------------------------------------------
    // /confirmar (bot-telegram-reservas, spec "Confirmar/Cancelar con OTP") — step 2 (D-4)
    // -------------------------------------------------------------------------

    private void handleConfirmar(ParsedUpdate update) {
        User user = requireLinked(update);
        if (user == null) {
            return;
        }
        String[] tokens = update.text().split("\\s+");
        if (tokens.length < 3) {
            telegramPort.enviarMensaje(update.chatId(),
                    "Formato: /confirmar <referencia> <código>. Ej: /confirmar a1b2c3d4 123456");
            return;
        }
        String ref = tokens[1];
        String code = tokens[2];

        ReservaResponse reserva = resolveOwnedReservation(user.getId(), ref);
        if (reserva == null) {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED, user.getId(),
                    "command=/confirmar,reason=not_found_or_not_owned");
            telegramPort.enviarMensaje(update.chatId(),
                    "No encuentro esa reserva entre las tuyas. Revisa la referencia con /misreservas.");
            return;
        }

        // Determine the pending operation by the active OTP type (D-4). Peeked read-only so a wrong
        // guess never burns a verification attempt against a valid code.
        OtpType pendingType;
        if (otpService.hasActiveCode(user.getId(), OtpType.CANCELLATION_CONFIRM)) {
            pendingType = OtpType.CANCELLATION_CONFIRM;
        } else if (otpService.hasActiveCode(user.getId(), OtpType.RESERVATION_CONFIRM)) {
            pendingType = OtpType.RESERVATION_CONFIRM;
        } else {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED, user.getId(),
                    "command=/confirmar,reason=no_pending_operation");
            telegramPort.enviarMensaje(update.chatId(),
                    "No hay ninguna operación pendiente de confirmar. Inicia una con /reservar o /cancelar.");
            return;
        }

        try {
            otpService.verify(user.getId(), pendingType, code);
        } catch (OtpVerificationException ex) {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED, user.getId(),
                    "command=/confirmar,reason=" + ex.getCode());
            telegramPort.enviarMensaje(update.chatId(), ex.getMessage());
            return;
        }

        UUID reservationId = UUID.fromString(reserva.id());
        try {
            if (pendingType == OtpType.CANCELLATION_CONFIRM) {
                cancelarReservaService.cancelar(reservationId, user.getId(), false);
                auditRecorder.record(TelegramAuditActions.TELEGRAM_RESERVA_CANCELLED, user.getId(),
                        "reservationId=" + reserva.id());
                telegramPort.enviarMensaje(update.chatId(),
                        "Tu reserva " + shortRef(reserva.id()) + " ha sido cancelada.");
            } else {
                confirmarReservaService.confirmar(reservationId, user.getId());
                auditRecorder.record(TelegramAuditActions.TELEGRAM_RESERVA_CONFIRMED, user.getId(),
                        "reservationId=" + reserva.id());
                telegramPort.enviarMensaje(update.chatId(),
                        "Tu reserva " + shortRef(reserva.id()) + " ha sido confirmada.");
            }
        } catch (SlotConflictException | InvalidReservaStateException | ValidationException
                 | ReservaForbiddenException | ReservaNotFoundException ex) {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED, user.getId(),
                    "command=/confirmar,reason=" + ex.getClass().getSimpleName());
            telegramPort.enviarMensaje(update.chatId(), "No se pudo completar la operación: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve the account behind the chat, or reply "link first" and return {@code null}. Every
     * business command funnels through here so an unlinked chat can never trigger a business operation.
     */
    private User requireLinked(ParsedUpdate update) {
        Optional<User> linked = userRepositoryPort.findByTelegramChatId(update.chatId());
        if (linked.isEmpty()) {
            telegramPort.enviarMensaje(update.chatId(), "Vincula primero tu cuenta en " + profileUrl);
            return null;
        }
        return linked.get();
    }

    /**
     * Resolve a short reference (8-hex UUID prefix, D-2) or a full UUID to a reservation the user
     * <em>owns</em>, searching only the caller's own reservations (BOLA prevention — a reservation the
     * user does not own is indistinguishable from a non-existent one). Returns {@code null} when the
     * reference matches no owned reservation or is ambiguous.
     */
    private ReservaResponse resolveOwnedReservation(Long userId, String ref) {
        String needle = normalizeRef(ref);
        if (needle.isEmpty()) {
            return null;
        }
        List<ReservaResponse> matches = new ArrayList<>();
        for (ReservaResponse r : reservaQueryService.listForUser(userId)) {
            if (!userId.equals(r.ownerId())) {
                continue; // only the owner may confirm/cancel (RN-AUTH-02)
            }
            if (normalizeRef(r.id()).startsWith(needle)) {
                matches.add(r);
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private CrearReservaRequest parseReservar(String text) {
        String[] tokens = text.split("\\s+");
        if (tokens.length < 3
                || !DATE_TOKEN.matcher(tokens[1]).matches()
                || !TIME_TOKEN.matcher(tokens[2]).matches()) {
            throw new CommandFormatException(
                    "Formato: /reservar <fecha AAAA-MM-DD> <hora HH:mm> [duración] [@jugador...]\n"
                            + "Ejemplo: /reservar 2026-08-01 18:00 90 @juan");
        }
        String date = tokens[1];
        String time = tokens[2];

        int idx = 3;
        Integer duration = DEFAULT_DURATION_MINUTES;
        if (tokens.length > 3 && DURATION_TOKEN.matcher(tokens[3]).matches()) {
            duration = Integer.parseInt(tokens[3]);
            idx = 4;
        }

        List<CrearReservaRequest.ParticipanteAdicional> participants = new ArrayList<>();
        for (int i = idx; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.startsWith("@")) {
                String handle = token.substring(1);
                User participant = userRepositoryPort.findByLogin(handle)
                        .orElseThrow(() -> new ParticipantResolutionException(
                                "No se pudo añadir a @" + handle + ": no es un usuario de PadelPro. "
                                        + "Revisa el identificador o añádelo como invitado (sin @)."));
                participants.add(new CrearReservaRequest.ParticipanteAdicional(participant.getId(), null, null));
            } else {
                participants.add(new CrearReservaRequest.ParticipanteAdicional(null, token, null));
            }
        }

        return new CrearReservaRequest(date, time, duration, null,
                participants.isEmpty() ? null : participants);
    }

    /** Short reference shown in chat: the first 8 hex characters of the reservation UUID (D-2). */
    private static String shortRef(String reservationId) {
        String hex = normalizeRef(reservationId);
        return hex.length() >= 8 ? hex.substring(0, 8) : hex;
    }

    private static String normalizeRef(String ref) {
        return ref == null ? "" : ref.toLowerCase(Locale.ROOT).replace("-", "").trim();
    }

    private static String estado(String status) {
        return switch (status) {
            case "PENDING_CONFIRMATION" -> "pendiente de confirmación";
            case "CONFIRMED" -> "confirmada";
            case "CANCELLED" -> "cancelada";
            case "COMPLETED" -> "completada";
            default -> status;
        };
    }

    // -------------------------------------------------------------------------
    // Internal value types
    // -------------------------------------------------------------------------

    private record ParsedUpdate(String chatId, String text, String updateId) {
    }

    /** Raised by the strict {@code /reservar} parser when the text does not match the expected format. */
    private static final class CommandFormatException extends RuntimeException {
        CommandFormatException(String message) {
            super(message);
        }
    }

    /** Raised when a {@code @handle} participant cannot be resolved to a linked account (D-5). */
    private static final class ParticipantResolutionException extends RuntimeException {
        ParticipantResolutionException(String message) {
            super(message);
        }
    }
}
