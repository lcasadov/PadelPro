package com.padelpro.auth.domain.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA entity representing an immutable audit log entry.
 *
 * <p>Maps to the {@code audit_log} table created by V3__create_audit_log_table.sql.
 * {@code user} is nullable: a failed login attempt with an unknown email generates
 * a row with {@code user_id = NULL} (requirement R-5.3). Rows are never deleted
 * (RGPD Art. 5 — minimum 2-year retention, RN-RGPD-02).
 *
 * <p>Known actions in bootstrap-mvp: USER_REGISTERED, LOGIN_SUCCESS,
 * LOGIN_FAILURE, TOKEN_TAMPERED.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Domain entity type affected (e.g. "USER", "RESERVATION"). Nullable. */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** Identifier of the affected entity. Nullable. */
    @Column(name = "entity_id", length = 100)
    private String entityId;

    /**
     * Channel through which the action was triggered (e.g. "WEB", "WHATSAPP", "API").
     * Defaults to "WEB" at the database level; nullable here until V5 migration runs.
     */
    @Column(name = "channel", length = 20)
    private String channel;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    protected AuditLog() {
        // JPA requires a no-arg constructor
    }

    public AuditLog(String action, User user, String ipAddress, String details,
                    OffsetDateTime createdAt) {
        this.action = action;
        this.user = user;
        this.ipAddress = ipAddress;
        this.details = details;
        this.createdAt = createdAt;
    }

    /**
     * Full constructor including the affected entity reference ({@code entity_type} / {@code entity_id}).
     * Used e.g. by the RGPD anonymization flow to record which USER was anonymized while the
     * {@code user} field references the executing admin (capability exportaciones-rgpd, RN-RGPD-01).
     */
    public AuditLog(String action, User user, String ipAddress, String details,
                    OffsetDateTime createdAt, String entityType, String entityId) {
        this(action, user, ipAddress, details, createdAt);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    // -------------------------------------------------------------------------
    // Getters (no setters — audit log entries are immutable after creation)
    // -------------------------------------------------------------------------

    public Long getId() { return id; }

    public String getAction() { return action; }

    public User getUser() { return user; }

    public String getIpAddress() { return ipAddress; }

    public String getDetails() { return details; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public String getEntityType() { return entityType; }

    public String getEntityId() { return entityId; }

    public String getChannel() { return channel; }
}
