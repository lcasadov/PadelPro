package com.padelpro.notificaciones.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit record of a single notification delivery (change {@code notificaciones-eventos-email}, D2 —
 * maps table {@code notification_log}).
 *
 * <p>An entry is created as {@link NotificationStatus#PENDING} before the send is attempted and then
 * moves to {@link NotificationStatus#SENT} or {@link NotificationStatus#FAILED}. The retry job
 * (Req 3) re-attempts {@code FAILED} entries while {@link #attempts} is below the cap.
 *
 * <p><b>RN-RGPD-04:</b> neither {@link #message} nor {@link #recipient} may ever contain passwords,
 * tokens, OTPs or card data — only reservation/payment facts (date, amount, reference). Callers are
 * responsible for building safe content; the {@code error_message} is a sanitized exception summary,
 * never the raw email body.
 *
 * <p>{@code type}/{@code status} are stored as plain {@code VARCHAR} (with DB CHECK constraints)
 * rather than native PostgreSQL enums, so the {@link EnumType#STRING} mapping works unchanged on both
 * PostgreSQL and H2 without the {@code NAMED_ENUM} cast the reservation/payment enums require.
 */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

    /** Max length persisted for the sanitized error summary (matches the column definition). */
    private static final int MAX_ERROR_LEN = 500;

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    @Column(name = "related_entity_id", length = 64)
    private String relatedEntityId;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected NotificationLog() {
    }

    /**
     * Factory for a new {@code PENDING} entry, recorded before the first delivery attempt.
     *
     * @param type              the notification channel (currently {@link NotificationType#EMAIL})
     * @param userId            the recipient user id, or {@code null} if not resolvable
     * @param recipient         the destination address (no sensitive data — RN-RGPD-04)
     * @param subject           the notification subject
     * @param message           the notification body (no sensitive data — RN-RGPD-04)
     * @param relatedEntityType the source aggregate type (e.g. {@code RESERVATION}, {@code PAYMENT})
     * @param relatedEntityId   the source aggregate id, as text
     */
    public static NotificationLog pending(NotificationType type, Long userId, String recipient,
                                          String subject, String message,
                                          String relatedEntityType, String relatedEntityId) {
        NotificationLog n = new NotificationLog();
        n.type = type;
        n.userId = userId;
        n.recipient = recipient;
        n.subject = subject;
        n.message = message;
        n.relatedEntityType = relatedEntityType;
        n.relatedEntityId = relatedEntityId;
        n.status = NotificationStatus.PENDING;
        n.attempts = 0;
        return n;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = NotificationStatus.PENDING;
        }
    }

    /**
     * Record a successful delivery: {@code SENT}, stamp {@code sent_at}, clear any prior error and
     * count this attempt.
     */
    public void markSent(OffsetDateTime at) {
        this.attempts++;
        this.lastAttemptAt = at;
        this.sentAt = at;
        this.errorMessage = null;
        this.status = NotificationStatus.SENT;
    }

    /**
     * Record a failed delivery: {@code FAILED}, store a sanitized error summary (never the email body
     * nor any secret — RN-RGPD-04) and count this attempt.
     */
    public void markFailed(String error, OffsetDateTime at) {
        this.attempts++;
        this.lastAttemptAt = at;
        this.status = NotificationStatus.FAILED;
        this.errorMessage = truncate(error);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRelatedEntityType() {
        return relatedEntityType;
    }

    public String getRelatedEntityId() {
        return relatedEntityId;
    }

    public int getAttempts() {
        return attempts;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public OffsetDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (id == null) return false;
        if (o == null || getClass() != o.getClass()) return false;
        NotificationLog that = (NotificationLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
