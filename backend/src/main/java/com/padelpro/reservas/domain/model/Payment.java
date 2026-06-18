package com.padelpro.reservas.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment of a reservation (maps table {@code payments}, data-model §3.5).
 *
 * <p>Created atomically with its reservation (1:1, D3). The {@code amount} is frozen at creation
 * time ({@code price_per_hour × duration_minutes / 60}); later changes to {@code price_per_hour}
 * never alter existing payments (US-024).
 *
 * <p>Enum columns are mapped as {@code EnumType.STRING}; Hibernate sends the text value which
 * PostgreSQL casts to the native enum type, and which H2 (PostgreSQL mode) stores as varchar.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "redsys_order_id")
    private String redsysOrderId;

    @Column(name = "payment_url")
    private String paymentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway")
    private PaymentGateway gateway;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "registered_by_id")
    private Long registeredById;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Payment() {
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Factory for the PENDING payment created together with a reservation (D3).
     *
     * @param reservationId the owning reservation UUID
     * @param amount        the frozen amount (must be &gt; 0, DB chk_pay_amount)
     */
    public static Payment pendingFor(UUID reservationId, BigDecimal amount) {
        Payment p = new Payment();
        p.reservationId = reservationId;
        p.amount = amount;
        p.status = PaymentStatus.PENDING;
        return p;
    }

    /** Mark this payment refunded (cancellation within deadline of a PAID payment). */
    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getRedsysOrderId() {
        return redsysOrderId;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public PaymentGateway getGateway() {
        return gateway;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Long getRegisteredById() {
        return registeredById;
    }

    public void setRegisteredById(Long registeredById) {
        this.registeredById = registeredById;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (id == null) return false;
        if (o == null || getClass() != o.getClass()) return false;
        Payment that = (Payment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
