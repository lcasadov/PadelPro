package com.padelpro.notificaciones.application.service;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import com.padelpro.mensajeria.domain.port.out.TelegramSendResult;
import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.model.NotificationStatus;
import com.padelpro.notificaciones.domain.model.NotificationType;
import com.padelpro.notificaciones.domain.port.out.NotificationLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link TelegramNotificationService} (tasks 4.1–4.4): record + deliver over
 * {@link TelegramPort}, fault-tolerant (RN-NOT-01), with group broadcast gated on
 * {@code telegram_group_id} (Req 6).
 *
 * <p>Uses hand-rolled fakes (mirrors {@link EmailNotificationServiceTest}) so the
 * {@link NotificationLog} transitions are observed on the very instances persisted.
 */
@DisplayName("TelegramNotificationService — record + deliver over Telegram, fault-tolerant")
class TelegramNotificationServiceTest {

    private static final class FakeLogPort implements NotificationLogPort {
        final List<NotificationLog> saved = new ArrayList<>();

        @Override
        public NotificationLog save(NotificationLog entry) {
            saved.add(entry);
            return entry;
        }

        @Override
        public Optional<NotificationLog> findById(UUID id) {
            return saved.stream().filter(n -> id.equals(n.getId())).findFirst();
        }

        @Override
        public List<NotificationLog> findRetriable(int maxAttempts) {
            return List.of();
        }

        NotificationLog last() {
            return saved.get(saved.size() - 1);
        }
    }

    /**
     * Captures the last chat/text and returns a configurable {@link TelegramSendResult}. Defaults to
     * {@code sent()}; {@code result} can be set to a failed/skipped outcome, or {@code throwOnSend} to
     * exercise the service's defensive catch of an unexpected throw.
     */
    private static final class FakeTelegramPort implements TelegramPort {
        String lastChatId;
        String lastText;
        int calls;
        boolean throwOnSend;
        TelegramSendResult result = TelegramSendResult.sent();

        @Override
        public TelegramSendResult enviarMensaje(String chatId, String texto) {
            calls++;
            this.lastChatId = chatId;
            this.lastText = texto;
            if (throwOnSend) {
                throw new RuntimeException("telegram api error");
            }
            return result;
        }
    }

    private static final class FakeSystemConfigPort implements SystemConfigRepositoryPort {
        String groupId;

        @Override
        public Optional<SystemConfig> findById(Long id) {
            SystemConfig c = SystemConfig.builder().telegramGroupId(groupId).build();
            return Optional.of(c);
        }

        @Override
        public SystemConfig save(SystemConfig config) {
            return config;
        }
    }

    private FakeLogPort logPort;
    private FakeTelegramPort telegramPort;
    private FakeSystemConfigPort configPort;
    private TelegramNotificationService service;

    @BeforeEach
    void setUp() {
        logPort = new FakeLogPort();
        telegramPort = new FakeTelegramPort();
        configPort = new FakeSystemConfigPort();
        service = new TelegramNotificationService(logPort, telegramPort, configPort);
    }

    @Test
    @DisplayName("4.1 direct send records a TELEGRAM_DIRECT entry as SENT")
    void direct_records_sent_on_success() {
        service.dispatchDirect("chat-123", "Tu reserva está confirmada", 7L, "RESERVATION", "res-1");

        assertThat(telegramPort.calls).isEqualTo(1);
        assertThat(telegramPort.lastChatId).isEqualTo("chat-123");
        NotificationLog result = logPort.last();
        assertThat(result.getType()).isEqualTo(NotificationType.TELEGRAM_DIRECT);
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getRecipient()).isEqualTo("chat-123");
        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getRelatedEntityType()).isEqualTo("RESERVATION");
        assertThat(result.getSentAt()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("4.3 a real Telegram failure (port returns FAILED) is recorded as FAILED with the error")
    void direct_records_failed_when_port_returns_failed() {
        telegramPort.result = TelegramSendResult.failed("Forbidden: bot was blocked by the user");

        assertThatCode(() -> service.dispatchDirect(
                "chat-123", "cuerpo", 7L, "RESERVATION", "res-1"))
                .doesNotThrowAnyException();

        assertThat(telegramPort.calls).isEqualTo(1);
        NotificationLog result = logPort.last();
        assertThat(result.getType()).isEqualTo(NotificationType.TELEGRAM_DIRECT);
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getSentAt()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Forbidden: bot was blocked by the user");
    }

    @Test
    @DisplayName("4.3 an unexpected throw from the port is defensively recorded as FAILED, never propagates")
    void direct_records_failed_on_unexpected_throw() {
        telegramPort.throwOnSend = true;

        assertThatCode(() -> service.dispatchDirect(
                "chat-123", "cuerpo", 7L, "RESERVATION", "res-1"))
                .doesNotThrowAnyException();

        NotificationLog result = logPort.last();
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getErrorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("SKIPPED result (bot not configured) → no log entry, not audited as a failure")
    void direct_skipped_is_not_audited() {
        telegramPort.result = TelegramSendResult.skipped("telegram not configured");

        service.dispatchDirect("chat-123", "cuerpo", 7L, "RESERVATION", "res-1");

        assertThat(telegramPort.calls).isEqualTo(1);
        assertThat(logPort.saved).isEmpty();
    }

    @Test
    @DisplayName("blank chat id → no send and no log entry")
    void direct_blank_chat_id_is_noop() {
        service.dispatchDirect("   ", "cuerpo", 7L, "RESERVATION", "res-1");

        assertThat(telegramPort.calls).isZero();
        assertThat(logPort.saved).isEmpty();
    }

    @Test
    @DisplayName("4.4 group broadcast publishes a TELEGRAM_GROUP SENT entry when group is configured")
    void group_publishes_when_configured() {
        configPort.groupId = "-100999";

        service.dispatchGroup("Nueva reserva confirmada", "RESERVATION", "res-1");

        assertThat(telegramPort.calls).isEqualTo(1);
        assertThat(telegramPort.lastChatId).isEqualTo("-100999");
        NotificationLog result = logPort.last();
        assertThat(result.getType()).isEqualTo(NotificationType.TELEGRAM_GROUP);
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getRecipient()).isEqualTo("-100999");
    }

    @Test
    @DisplayName("4.4 group broadcast is skipped (no send, no log) when telegram_group_id is unset")
    void group_skipped_when_not_configured() {
        configPort.groupId = null;

        service.dispatchGroup("Nueva reserva confirmada", "RESERVATION", "res-1");

        assertThat(telegramPort.calls).isZero();
        assertThat(logPort.saved).isEmpty();
    }

    @Test
    @DisplayName("group broadcast failure (port returns FAILED) is recorded as FAILED and never propagates")
    void group_records_failed_on_failed_result() {
        configPort.groupId = "-100999";
        telegramPort.result = TelegramSendResult.failed("chat not found");

        assertThatCode(() -> service.dispatchGroup("cuerpo", "RESERVATION", "res-1"))
                .doesNotThrowAnyException();

        NotificationLog result = logPort.last();
        assertThat(result.getType()).isEqualTo(NotificationType.TELEGRAM_GROUP);
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("chat not found");
    }
}
