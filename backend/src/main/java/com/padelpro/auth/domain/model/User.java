package com.padelpro.auth.domain.model;

import jakarta.persistence.*;
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
}
