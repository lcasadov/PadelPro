package com.padelpro.reservas.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage unit tests for the {@link Payment} domain model (change backend-branch-coverage,
 * D1/D2). Exercises the state-transition helpers, the {@code onCreate} defaults and the identity
 * contract. Same package as {@link Payment} so the JPA-protected callbacks are reachable.
 */
@DisplayName("Unit — Payment (domain)")
class PaymentTest {

    private static final UUID RES = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    /** Set the private JPA id without a persistence context (no setter exists by design). */
    private static void setId(Payment p, UUID id) {
        try {
            var f = Payment.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("onCreate defaults")
    class OnCreate {

        @Test
        @DisplayName("assigns id/status/timestamps only when unset (true branches)")
        void populates_defaults() {
            Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
            // pendingFor already sets status=PENDING; null it to exercise the status==null branch.
            p.setStatus(null);

            p.onCreate();

            assertThat(p.getId()).isNotNull();
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(p.getCreatedAt()).isNotNull();
            assertThat(p.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("does not overwrite id/status/timestamps when already set (false branches)")
        void keeps_existing() {
            Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
            p.onCreate();                 // first assignment
            UUID id = p.getId();
            var createdAt = p.getCreatedAt();
            p.setStatus(PaymentStatus.PAID);

            p.onCreate();                 // everything set → no overwrite

            assertThat(p.getId()).isEqualTo(id);
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(p.getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("onUpdate refreshes updated_at")
        void on_update() {
            Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
            p.onUpdate();
            assertThat(p.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("state transitions")
    class Transitions {

        @Test
        @DisplayName("markInProgress sets Redsys gateway/method/order/url")
        void mark_in_progress() {
            Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
            p.markInProgress("ORDER123", "https://tpv");
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
            assertThat(p.getMethod()).isEqualTo(PaymentMethod.REDSYS);
            assertThat(p.getGateway()).isEqualTo(PaymentGateway.REDSYS);
            assertThat(p.getRedsysOrderId()).isEqualTo("ORDER123");
            assertThat(p.getPaymentUrl()).isEqualTo("https://tpv");
        }

        @Test
        @DisplayName("markPaid stores only the transaction id (never card data)")
        void mark_paid() {
            Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
            OffsetDateTime at = OffsetDateTime.now();
            p.markPaid("AUTH999", at);
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(p.getTransactionId()).isEqualTo("AUTH999");
            assertThat(p.getPaidAt()).isEqualTo(at);
        }

        @Test
        @DisplayName("markFailed / markRefunded / markCashPaid set their terminal statuses")
        void other_transitions() {
            Payment failed = Payment.pendingFor(RES, new BigDecimal("15.00"));
            failed.markFailed();
            assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);

            Payment refunded = Payment.pendingFor(RES, new BigDecimal("15.00"));
            refunded.markRefunded();
            assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

            Payment cash = Payment.pendingFor(RES, new BigDecimal("15.00"));
            OffsetDateTime at = OffsetDateTime.now();
            cash.markCashPaid(42L, at);
            assertThat(cash.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(cash.getMethod()).isEqualTo(PaymentMethod.CASH);
            assertThat(cash.getRegisteredById()).isEqualTo(42L);
            assertThat(cash.getPaidAt()).isEqualTo(at);
        }
    }

    @Nested
    @DisplayName("equals / hashCode identity contract")
    class Identity {

        @Test
        @DisplayName("reflexive")
        void reflexive() {
            Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
            setId(p, UUID.randomUUID());
            assertThat(p.equals(p)).isTrue();
        }

        @Test
        @DisplayName("transient (null id) never equal; hashCode falls back to identity")
        void transient_not_equal() {
            Payment a = Payment.pendingFor(RES, new BigDecimal("15.00"));
            Payment b = Payment.pendingFor(RES, new BigDecimal("15.00"));
            setId(b, UUID.randomUUID());
            assertThat(a.equals(b)).isFalse();
            assertThat(a.hashCode()).isEqualTo(System.identityHashCode(a));
        }

        @Test
        @DisplayName("not equal to null nor to a different type")
        void not_equal_null_or_other_type() {
            Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
            setId(p, UUID.randomUUID());
            assertThat(p.equals(null)).isFalse();
            assertThat(p.equals("x")).isFalse();
        }

        @Test
        @DisplayName("same id equal + same hashCode; different id not equal")
        void by_id() {
            UUID id = UUID.randomUUID();
            Payment a = Payment.pendingFor(RES, new BigDecimal("15.00"));
            Payment b = Payment.pendingFor(RES, new BigDecimal("15.00"));
            Payment c = Payment.pendingFor(RES, new BigDecimal("15.00"));
            setId(a, id);
            setId(b, id);
            setId(c, UUID.randomUUID());
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a).isNotEqualTo(c);
        }
    }
}
