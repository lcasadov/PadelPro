package com.padelpro.reservas.domain.model;

import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ReservationStateMachine} (task 8.1, D6).
 *
 * <pre>
 *   PENDING_CONFIRMATION → CONFIRMED → COMPLETED
 *                       ↘            ↘
 *                         CANCELLED
 * </pre>
 *
 * <p>{@code COMPLETED} and {@code CANCELLED} are terminal; any other transition (incl. same-state)
 * is rejected with 422 ({@link InvalidReservaStateException}, code {@code INVALID_STATE_TRANSITION}).
 */
@DisplayName("Unit — ReservationStateMachine")
class ReservationStateMachineTest {

    @Nested
    @DisplayName("canTransition")
    class CanTransition {

        @Test
        @DisplayName("permite las transiciones del diagrama D6")
        void should_allow_valid_transitions() {
            assertThat(ReservationStateMachine.canTransition(
                    ReservationStatus.PENDING_CONFIRMATION, ReservationStatus.CONFIRMED)).isTrue();
            assertThat(ReservationStateMachine.canTransition(
                    ReservationStatus.PENDING_CONFIRMATION, ReservationStatus.CANCELLED)).isTrue();
            assertThat(ReservationStateMachine.canTransition(
                    ReservationStatus.CONFIRMED, ReservationStatus.COMPLETED)).isTrue();
            assertThat(ReservationStateMachine.canTransition(
                    ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("rechaza saltar de PENDING a COMPLETED")
        void should_reject_pending_to_completed() {
            assertThat(ReservationStateMachine.canTransition(
                    ReservationStatus.PENDING_CONFIRMATION, ReservationStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("estados terminales no permiten ninguna salida")
        void should_have_no_outgoing_from_terminal_states() {
            for (ReservationStatus to : ReservationStatus.values()) {
                assertThat(ReservationStateMachine.canTransition(ReservationStatus.COMPLETED, to))
                        .as("COMPLETED → %s", to).isFalse();
                assertThat(ReservationStateMachine.canTransition(ReservationStatus.CANCELLED, to))
                        .as("CANCELLED → %s", to).isFalse();
            }
        }

        @Test
        @DisplayName("rechaza transiciones inversas (CONFIRMED → PENDING)")
        void should_reject_backwards_transition() {
            assertThat(ReservationStateMachine.canTransition(
                    ReservationStatus.CONFIRMED, ReservationStatus.PENDING_CONFIRMATION)).isFalse();
        }
    }

    @Nested
    @DisplayName("assertCanTransition")
    class AssertCanTransition {

        @Test
        @DisplayName("no lanza para una transición válida")
        void should_not_throw_for_valid_transition() {
            assertThatCode(() -> ReservationStateMachine.assertCanTransition(
                    ReservationStatus.PENDING_CONFIRMATION, ReservationStatus.CONFIRMED))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("lanza 422 INVALID_STATE_TRANSITION para transición inválida")
        void should_throw_422_for_invalid_transition() {
            assertThatThrownBy(() -> ReservationStateMachine.assertCanTransition(
                    ReservationStatus.PENDING_CONFIRMATION, ReservationStatus.COMPLETED))
                    .isInstanceOf(InvalidReservaStateException.class)
                    .satisfies(ex -> assertThat(((InvalidReservaStateException) ex).getCode())
                            .isEqualTo("INVALID_STATE_TRANSITION"));
        }

        @Test
        @DisplayName("lanza 422 cuando origen y destino son el mismo estado (no-op)")
        void should_throw_when_same_state() {
            assertThatThrownBy(() -> ReservationStateMachine.assertCanTransition(
                    ReservationStatus.CONFIRMED, ReservationStatus.CONFIRMED))
                    .isInstanceOf(InvalidReservaStateException.class)
                    .satisfies(ex -> assertThat(((InvalidReservaStateException) ex).getCode())
                            .isEqualTo("INVALID_STATE_TRANSITION"))
                    .hasMessageContaining("ya se encuentra");
        }

        @Test
        @DisplayName("lanza 422 al intentar cancelar una reserva ya cancelada")
        void should_throw_when_cancelling_cancelled() {
            assertThatThrownBy(() -> ReservationStateMachine.assertCanTransition(
                    ReservationStatus.CANCELLED, ReservationStatus.CANCELLED))
                    .isInstanceOf(InvalidReservaStateException.class);
        }
    }
}
