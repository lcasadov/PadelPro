package com.padelpro.otp.domain.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * JPA entity for a one-time password (auth-otp-telegram, RN-AUTH-07).
 *
 * <p>Maps to the {@code otp_codes} table created by
 * {@code V15__otp_codes_and_telegram_link.sql}. The clear code never touches this entity —
 * only its SHA-256 hex digest is persisted in {@code code_hash} (D-OTP-02, RN-RGPD-04).
 *
 * <p>Lifecycle (D-OTP-03): a code is valid while {@code used == false},
 * {@code attempts < 3} and {@code expires_at} is in the future. A correct verification or the
 * 3rd failed attempt both set {@code used = true} (single-use / auto-invalidation).
 */
@Entity
@Table(name = "otp_codes")
public class OtpCode {

    /** Maximum failed verification attempts before the code auto-invalidates (RN-AUTH-07). */
    public static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpType type;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected OtpCode() {
        // JPA
    }

    public OtpCode(Long userId, String codeHash, OtpType type,
                   OffsetDateTime expiresAt, OffsetDateTime createdAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.type = type;
        this.attempts = 0;
        this.used = false;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /** {@code true} when the code has expired at the given instant (RN-AUTH-07: TTL 10 min). */
    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** Mark the code as consumed (single-use). Idempotent. */
    public void markUsed() {
        this.used = true;
    }

    /**
     * Register a failed verification attempt. When the attempt count reaches {@link #MAX_ATTEMPTS}
     * the code auto-invalidates ({@code used = true}) — RN-AUTH-07 / D-OTP-03.
     *
     * @return {@code true} if this failure invalidated the code
     */
    public boolean registerFailedAttempt() {
        this.attempts++;
        if (this.attempts >= MAX_ATTEMPTS) {
            this.used = true;
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }

    public Long getUserId() { return userId; }

    public String getCodeHash() { return codeHash; }

    public OtpType getType() { return type; }

    public int getAttempts() { return attempts; }

    public boolean isUsed() { return used; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
