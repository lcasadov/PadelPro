package com.padelpro.notificaciones.application.service;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import com.padelpro.mensajeria.domain.port.out.TelegramSendResult;
import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.model.NotificationType;
import com.padelpro.notificaciones.domain.port.out.NotificationLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Orchestrates Telegram delivery + audit for the {@code notificaciones} capability (change
 * {@code notificaciones-telegram}, D1/D3). Sits next to {@link EmailNotificationService} and follows
 * the same record→send→update flow, but over {@link TelegramPort} instead of SMTP:
 * <ol>
 *   <li>send through {@link TelegramPort#enviarMensaje} (best-effort, never throws, returns a result);</li>
 *   <li>record a {@link NotificationLog} entry ({@code TELEGRAM_DIRECT} / {@code TELEGRAM_GROUP}) as
 *       {@code SENT} or {@code FAILED} + a sanitized error, <em>according to the real send result</em>
 *       (spec Req 2 scenarios 3/4). A {@code SKIPPED} result (bot not configured) records nothing —
 *       it is a clean degradation, not a real failure.</li>
 * </ol>
 *
 * <p><b>RN-NOT-01 (fault tolerance):</b> a Telegram failure is captured as a {@code FAILED} log entry
 * and never propagates — the reservation/payment business flow (and the email that was already
 * dispatched) is unaffected.
 *
 * <p><b>No retry:</b> Telegram entries are audit-only; the retry job re-sends {@code EMAIL} entries
 * only, so a {@code FAILED} Telegram entry is never re-attempted as an email.
 *
 * <p><b>Note on {@code subject}:</b> {@code notification_log.subject} is {@code NOT NULL} (it was
 * introduced for the email channel). Telegram messages carry no subject, so a short channel label is
 * stored to satisfy the column while keeping the audit row meaningful.
 *
 * <p><b>RN-RGPD-04:</b> the {@code error_message} stored on failure is the exception class name, never
 * the message body nor any secret; {@code recipient} is the chat/group id, never a token.
 */
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    /** Placeholder subject for the NOT NULL column — Telegram messages have no real subject. */
    private static final String SUBJECT = "Telegram";

    private final NotificationLogPort notificationLogPort;
    private final TelegramPort telegramPort;
    private final SystemConfigRepositoryPort systemConfigRepositoryPort;
    private final Supplier<OffsetDateTime> clock;

    public TelegramNotificationService(NotificationLogPort notificationLogPort,
                                       TelegramPort telegramPort,
                                       SystemConfigRepositoryPort systemConfigRepositoryPort) {
        this(notificationLogPort, telegramPort, systemConfigRepositoryPort, OffsetDateTime::now);
    }

    /** Test constructor allowing a fixed clock for deterministic assertions. */
    TelegramNotificationService(NotificationLogPort notificationLogPort,
                                TelegramPort telegramPort,
                                SystemConfigRepositoryPort systemConfigRepositoryPort,
                                Supplier<OffsetDateTime> clock) {
        this.notificationLogPort = notificationLogPort;
        this.telegramPort = telegramPort;
        this.systemConfigRepositoryPort = systemConfigRepositoryPort;
        this.clock = clock;
    }

    /**
     * Record and deliver a direct Telegram message to a linked recipient (RN-TEL-03). Never throws.
     *
     * @param chatId            the recipient's Telegram chat id (already known to be non-blank)
     * @param text              the message body (no secrets — RN-RGPD-04)
     * @param userId            the recipient user id, or {@code null} if not resolvable
     * @param relatedEntityType source aggregate type (e.g. {@code RESERVATION}, {@code PAYMENT})
     * @param relatedEntityId   source aggregate id, as text
     */
    @Async("notificationsTaskExecutor")
    public void dispatchDirect(String chatId, String text, Long userId,
                               String relatedEntityType, String relatedEntityId) {
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        send(NotificationType.TELEGRAM_DIRECT, chatId, text, userId, relatedEntityType, relatedEntityId);
    }

    /**
     * Record and deliver a broadcast to the club's Telegram group when
     * {@code system_config.telegram_group_id} is configured (Req 6). When the group is not configured,
     * nothing is sent and no log entry is created (clean degradation). Never throws.
     *
     * @param text              the message body (no personal data — RN-RGPD)
     * @param relatedEntityType source aggregate type (e.g. {@code RESERVATION})
     * @param relatedEntityId   source aggregate id, as text
     */
    @Async("notificationsTaskExecutor")
    public void dispatchGroup(String text, String relatedEntityType, String relatedEntityId) {
        Optional<String> groupId = groupId();
        if (groupId.isEmpty()) {
            log.debug("Telegram group not configured — skipping group broadcast");
            return;
        }
        send(NotificationType.TELEGRAM_GROUP, groupId.get(), text, null,
                relatedEntityType, relatedEntityId);
    }

    /** Attempt the send, then record SENT/FAILED per the real result (nothing for SKIPPED). Never throws. */
    private void send(NotificationType type, String recipient, String text, Long userId,
                      String relatedEntityType, String relatedEntityId) {
        TelegramSendResult result;
        try {
            result = telegramPort.enviarMensaje(recipient, text);
        } catch (Exception ex) {
            // The port contract is best-effort and must not throw; defensively treat an unexpected
            // throw as a failure so it is still audited (RN-NOT-01).
            result = TelegramSendResult.failed(ex.getClass().getSimpleName());
        }

        if (result.isSkipped()) {
            // RN-TEL-03: the bot is not configured — no real attempt was made, so nothing is audited.
            log.debug("Telegram {} to {} skipped ({})", type, recipient, result.error());
            return;
        }

        NotificationLog entry = NotificationLog.pending(
                type, userId, recipient, SUBJECT, text, relatedEntityType, relatedEntityId);
        if (result.isSent()) {
            entry.markSent(clock.get());
            log.info("Telegram {} to {} delivered", type, recipient);
        } else {
            entry.markFailed(result.error(), clock.get());
            log.warn("Telegram {} to {} failed: {}", type, recipient, result.error());
        }
        try {
            notificationLogPort.save(entry);
        } catch (Exception persistenceError) {
            log.warn("Could not persist Telegram notification outcome to {}: {}",
                    recipient, persistenceError.getClass().getSimpleName());
        }
    }

    private Optional<String> groupId() {
        return systemConfigRepositoryPort.findById(1L)
                .map(SystemConfig::getTelegramGroupId)
                .filter(id -> id != null && !id.isBlank());
    }
}
