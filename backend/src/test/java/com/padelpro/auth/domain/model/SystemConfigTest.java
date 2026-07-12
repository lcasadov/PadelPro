package com.padelpro.auth.domain.model;

import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage unit tests for the {@link SystemConfig} entity (change backend-branch-coverage,
 * D1/D2). Covers the {@code onCreate} default stamping and the identity contract. Same package so
 * the JPA-protected {@code onCreate}/{@code onUpdate} callbacks are reachable.
 */
@DisplayName("Unit — SystemConfig (domain)")
class SystemConfigTest {

    private static SystemConfig withId(Long id) {
        return SystemConfig.builder()
                .id(id)
                .clubName("Club")
                .clubDescription("Desc")
                .pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH)
                .maxParticipantsPerPista(4)
                .pricePerHour(new BigDecimal("10.00"))
                .cancellationDeadlineHours(24)
                .build();
    }

    @Nested
    @DisplayName("onCreate / onUpdate")
    class Lifecycle {

        @Test
        @DisplayName("onCreate stamps createdAt/updatedAt only when unset (true branches)")
        void onCreate_stamps_when_unset() {
            SystemConfig c = withId(1L); // builder leaves createdAt/updatedAt null
            c.onCreate();
            assertThat(c.getCreatedAt()).isNotNull();
            assertThat(c.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("onCreate does not overwrite existing timestamps (false branches)")
        void onCreate_keeps_existing() {
            OffsetDateTime fixed = OffsetDateTime.now().minusDays(1);
            SystemConfig c = SystemConfig.builder()
                    .id(1L).clubName("Club").pistaState(PistaState.ACTIVA)
                    .paymentGateway(PaymentGateway.CASH).maxParticipantsPerPista(4)
                    .pricePerHour(new BigDecimal("10.00")).cancellationDeadlineHours(24)
                    .createdAt(fixed).updatedAt(fixed)
                    .build();
            c.onCreate();
            assertThat(c.getCreatedAt()).isEqualTo(fixed);
            assertThat(c.getUpdatedAt()).isEqualTo(fixed);
        }

        @Test
        @DisplayName("onUpdate refreshes updatedAt")
        void onUpdate() {
            SystemConfig c = withId(1L);
            c.onUpdate();
            assertThat(c.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("equals / hashCode identity contract")
    class Identity {

        @Test
        @DisplayName("reflexive")
        void reflexive() {
            SystemConfig c = withId(1L);
            assertThat(c.equals(c)).isTrue();
        }

        @Test
        @DisplayName("transient (null id) never equal; hashCode falls back to identity")
        void transient_not_equal() {
            SystemConfig a = withId(null);
            SystemConfig b = withId(1L);
            assertThat(a.equals(b)).isFalse();
            assertThat(a.hashCode()).isEqualTo(System.identityHashCode(a));
        }

        @Test
        @DisplayName("not equal to null nor to a different type")
        void not_equal_null_or_other_type() {
            SystemConfig c = withId(1L);
            assertThat(c.equals(null)).isFalse();
            assertThat(c.equals("x")).isFalse();
        }

        @Test
        @DisplayName("same id equal + same hashCode; different id not equal")
        void by_id() {
            SystemConfig a = withId(1L);
            SystemConfig b = withId(1L);
            SystemConfig c = withId(2L);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a).isNotEqualTo(c);
        }
    }
}
