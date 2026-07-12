package com.padelpro.reservas.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * <p>The {@code method}, {@code status} and {@code gateway} columns are native PostgreSQL enum
 * types (payment_method, payment_status, payment_gateway). PostgreSQL does not implicitly cast a
 * bound varchar to a native enum, so they are bound via {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)}
 * with {@code columnDefinition} set to the Flyway-created type name (so ddl-auto=validate matches).
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
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "method", columnDefinition = "payment_method")
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "payment_status")
    private PaymentStatus status;

    @Column(name = "redsys_order_id")
    private String redsysOrderId;

    @Column(name = "payment_url")
    private String paymentUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gateway", columnDefinition = "payment_gateway")
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

    /**
     * Move a PENDING payment to IN_PROGRESS when the owner starts the Redsys checkout
     * (pagos-redsys-online, D5). Records the generated Redsys order id and the TPV URL used for the
     * browser redirect. Card data never touches this entity (RN-PAY-03).
     */
    public void markInProgress(String redsysOrderId, String paymentUrl) {
        this.status = PaymentStatus.IN_PROGRESS;
        this.method = PaymentMethod.REDSYS;
        this.gateway = PaymentGateway.REDSYS;
        this.redsysOrderId = redsysOrderId;
        this.paymentUrl = paymentUrl;
    }

    /**
     * Confirm a Redsys payment from a valid webhook with an approved {@code Ds_Response}
     * (pagos-redsys-online, D3). Only {@code transaction_id} (Ds_AuthorisationCode) is stored — never
     * card data (RN-PAY-03).
     */
    public void markPaid(String transactionId, OffsetDateTime paidAt) {
        this.status = PaymentStatus.PAID;
        this.transactionId = transactionId;
        this.paidAt = paidAt;
    }

    /** Mark a Redsys payment as FAILED from a valid webhook with a rejection {@code Ds_Response} (D3). */
    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    /**
     * Confirm a payment through the provisional online simulator (change pagos-simulador-gestion, D2):
     * PAID / SIMULADO. No card data ever reaches this entity (RN-RGPD-04); the simulator only decides
     * the outcome in memory. Kept separate from {@link #markPaid} / {@link #markCashPaid} so a simulated
     * collection is distinguishable from a real Redsys or cash one.
     */
    public void markSimulatedPaid(OffsetDateTime paidAt) {
        this.status = PaymentStatus.PAID;
        this.method = PaymentMethod.SIMULADO;
        this.paidAt = paidAt;
    }

    /**
     * Register a manual cash payment by an ADMIN (pagos-redsys-online, group 5): PAID / CASH with the
     * registering admin id (DB CHECK {@code chk_pay_cash_admin} requires {@code registered_by_id}).
     */
    public void markCashPaid(Long registeredById, OffsetDateTime paidAt) {
        this.status = PaymentStatus.PAID;
        this.method = PaymentMethod.CASH;
        this.registeredById = registeredById;
        this.paidAt = paidAt;
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
