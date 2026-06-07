package com.padelpro.auth.domain.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @Column(columnDefinition = "integer check (id = 1)")
    private Long id;

    @Column(nullable = false)
    private String clubName;

    @Column(columnDefinition = "TEXT")
    private String clubDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PistaState pistaState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentGateway paymentGateway;

    @Column(columnDefinition = "TEXT")
    private String telegramBotToken;

    @Column(columnDefinition = "TEXT")
    private String redsysMerchantId;

    @Column(columnDefinition = "TEXT")
    private String redsysMerchantKey;

    @Column(nullable = false)
    private Integer maxParticipantsPerPista;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    public SystemConfig() {
    }

    public SystemConfig(Long id, String clubName, String clubDescription, PistaState pistaState,
                        PaymentGateway paymentGateway, String telegramBotToken, String redsysMerchantId,
                        String redsysMerchantKey, Integer maxParticipantsPerPista, OffsetDateTime createdAt,
                        OffsetDateTime updatedAt, Long updatedByUserId) {
        this.id = id;
        this.clubName = clubName;
        this.clubDescription = clubDescription;
        this.pistaState = pistaState;
        this.paymentGateway = paymentGateway;
        this.telegramBotToken = telegramBotToken;
        this.redsysMerchantId = redsysMerchantId;
        this.redsysMerchantKey = redsysMerchantKey;
        this.maxParticipantsPerPista = maxParticipantsPerPista;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.updatedByUserId = updatedByUserId;
    }

    public static SystemConfigBuilder builder() {
        return new SystemConfigBuilder();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClubName() {
        return clubName;
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
    }

    public String getClubDescription() {
        return clubDescription;
    }

    public void setClubDescription(String clubDescription) {
        this.clubDescription = clubDescription;
    }

    public PistaState getPistaState() {
        return pistaState;
    }

    public void setPistaState(PistaState pistaState) {
        this.pistaState = pistaState;
    }

    public PaymentGateway getPaymentGateway() {
        return paymentGateway;
    }

    public void setPaymentGateway(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public void setTelegramBotToken(String telegramBotToken) {
        this.telegramBotToken = telegramBotToken;
    }

    public String getRedsysMerchantId() {
        return redsysMerchantId;
    }

    public void setRedsysMerchantId(String redsysMerchantId) {
        this.redsysMerchantId = redsysMerchantId;
    }

    public String getRedsysMerchantKey() {
        return redsysMerchantKey;
    }

    public void setRedsysMerchantKey(String redsysMerchantKey) {
        this.redsysMerchantKey = redsysMerchantKey;
    }

    public Integer getMaxParticipantsPerPista() {
        return maxParticipantsPerPista;
    }

    public void setMaxParticipantsPerPista(Integer maxParticipantsPerPista) {
        this.maxParticipantsPerPista = maxParticipantsPerPista;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(Long updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SystemConfig that = (SystemConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public enum PaymentGateway {
        CASH,
        REDSYS
    }

    public enum PistaState {
        ACTIVA,
        MANTENIMIENTO
    }

    public static class SystemConfigBuilder {
        private Long id;
        private String clubName;
        private String clubDescription;
        private PistaState pistaState;
        private PaymentGateway paymentGateway;
        private String telegramBotToken;
        private String redsysMerchantId;
        private String redsysMerchantKey;
        private Integer maxParticipantsPerPista;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private Long updatedByUserId;

        public SystemConfigBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public SystemConfigBuilder clubName(String clubName) {
            this.clubName = clubName;
            return this;
        }

        public SystemConfigBuilder clubDescription(String clubDescription) {
            this.clubDescription = clubDescription;
            return this;
        }

        public SystemConfigBuilder pistaState(PistaState pistaState) {
            this.pistaState = pistaState;
            return this;
        }

        public SystemConfigBuilder paymentGateway(PaymentGateway paymentGateway) {
            this.paymentGateway = paymentGateway;
            return this;
        }

        public SystemConfigBuilder telegramBotToken(String telegramBotToken) {
            this.telegramBotToken = telegramBotToken;
            return this;
        }

        public SystemConfigBuilder redsysMerchantId(String redsysMerchantId) {
            this.redsysMerchantId = redsysMerchantId;
            return this;
        }

        public SystemConfigBuilder redsysMerchantKey(String redsysMerchantKey) {
            this.redsysMerchantKey = redsysMerchantKey;
            return this;
        }

        public SystemConfigBuilder maxParticipantsPerPista(Integer maxParticipantsPerPista) {
            this.maxParticipantsPerPista = maxParticipantsPerPista;
            return this;
        }

        public SystemConfigBuilder createdAt(OffsetDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public SystemConfigBuilder updatedAt(OffsetDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public SystemConfigBuilder updatedByUserId(Long updatedByUserId) {
            this.updatedByUserId = updatedByUserId;
            return this;
        }

        public SystemConfig build() {
            return new SystemConfig(id, clubName, clubDescription, pistaState, paymentGateway,
                    telegramBotToken, redsysMerchantId, redsysMerchantKey, maxParticipantsPerPista,
                    createdAt, updatedAt, updatedByUserId);
        }
    }
}
