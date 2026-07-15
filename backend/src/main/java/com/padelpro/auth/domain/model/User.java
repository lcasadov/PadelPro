package com.padelpro.auth.domain.model;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * JPA entity representing a platform user.
 *
 * <p>Maps to the {@code users} table created by V1__create_users_table.sql.
 * Enum fields use {@link EnumType#STRING} to stay coherent with the VARCHAR + CHECK
 * constraints in the migration (no native PG ENUM — simpler ALTER in future migrations).
 *
 * <p>Soft-delete is implemented via {@code status = INACTIVE}; rows are never hard-deleted.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String login;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(length = 20)
    private String phone;

    @Column(name = "telegram_chat_id", length = 50)
    private String telegramChatId;

    @Column(name = "telegram_linked_at")
    private OffsetDateTime telegramLinkedAt;

    /**
     * Consecutive failed-login counter (account lockout, H-1 / OWASP A07). Incremented on every
     * failed attempt and reset to 0 on a successful login or when the threshold triggers a lock.
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /**
     * Instant until which the account is temporarily locked after too many failed logins
     * ({@code null} = not locked). Independent of the source IP, so it survives IP rotation
     * that would otherwise defeat the per-IP rate limiter.
     */
    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    protected User() {
        // JPA requires a no-arg constructor
    }

    public User(String login, String passwordHash, String firstName, String lastName,
                String email, UserRole role, UserStatus status,
                OffsetDateTime registeredAt, OffsetDateTime updatedAt) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
        this.status = status;
        this.registeredAt = registeredAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------------------------
    // Getters & setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(String telegramChatId) { this.telegramChatId = telegramChatId; }

    public OffsetDateTime getTelegramLinkedAt() { return telegramLinkedAt; }
    public void setTelegramLinkedAt(OffsetDateTime telegramLinkedAt) { this.telegramLinkedAt = telegramLinkedAt; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public OffsetDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(OffsetDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    // -------------------------------------------------------------------------
    // Account-lockout domain behaviour (H-1 / OWASP A07 — brute-force defence)
    // -------------------------------------------------------------------------

    /**
     * @return {@code true} if the account is currently locked (i.e. {@code lockedUntil} is set and
     *         still in the future relative to {@code now}).
     */
    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Records one failed login. When the consecutive-failure count reaches {@code maxAttempts} the
     * account is locked for {@code lockoutDuration} and the counter is reset to 0 (so a fresh set of
     * attempts is available once the lock expires). No-op while already locked, so repeated attempts
     * during a lock window do not indefinitely extend it.
     *
     * @param maxAttempts     consecutive failures that trigger a lock (must be ≥ 1)
     * @param lockoutDuration how long the lock lasts once triggered
     * @param now             current instant
     */
    public void registerFailedLogin(int maxAttempts, Duration lockoutDuration, OffsetDateTime now) {
        if (isLocked(now)) {
            return;
        }
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = now.plus(lockoutDuration);
            this.failedLoginAttempts = 0;
        }
    }

    /** Clears any accumulated failure state (called after a successful authentication). */
    public void clearFailedLogins() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }
}
