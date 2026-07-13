package com.padelpro.bloqueos.application.service;

import com.padelpro.bloqueos.application.dto.BloqueoResponse;
import com.padelpro.bloqueos.domain.audit.BloqueoAuditActions;
import com.padelpro.bloqueos.domain.exception.BloqueoConflictException;
import com.padelpro.bloqueos.domain.model.BloqueoPista;
import com.padelpro.bloqueos.infrastructure.persistence.BloqueoJpaRepository;
import com.padelpro.reservas.application.service.DisponibilidadCacheInvalidator;
import com.padelpro.reservas.domain.model.ReservationOccupancy;
import com.padelpro.reservas.domain.port.out.ReservationQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BloqueoService} — court blocks (change bloqueos-pista-eventos, group 2).
 * Pure logic with all outbound collaborators mocked; no database involved.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BloqueoService — creación / conflicto / idempotencia / borrado")
class BloqueoServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 1);
    private static final long ADMIN_ID = 99L;
    private static final LocalTime H18 = LocalTime.of(18, 0);
    private static final LocalTime H19 = LocalTime.of(19, 0);

    @Mock private BloqueoJpaRepository repository;
    @Mock private ReservationQueryPort reservationQueryPort;
    @Mock private DisponibilidadCacheInvalidator cacheInvalidator;
    @Mock private BloqueoAuditRecorder auditRecorder;

    private BloqueoService service;

    @BeforeEach
    void setUp() {
        service = new BloqueoService(repository, reservationQueryPort, cacheInvalidator, auditRecorder);
    }

    // crear OK — sin conflicto: inserta las horas que faltan, invalida caché y audita
    @Test
    @DisplayName("crear OK → inserta franjas libres, invalida caché y audita BLOQUEO_CREATED")
    void crear_ok_inserts_and_invalidates() {
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of());
        when(repository.existsByFechaAndHora(FECHA, H18)).thenReturn(false);
        when(repository.existsByFechaAndHora(FECHA, H19)).thenReturn(false);
        when(repository.findByFechaOrderByHora(FECHA)).thenReturn(List.of(
                new BloqueoPista(FECHA, H18, "Torneo", ADMIN_ID),
                new BloqueoPista(FECHA, H19, "Torneo", ADMIN_ID)));

        List<BloqueoResponse> creados = service.crear(FECHA, List.of(H18, H19), "Torneo", ADMIN_ID);

        assertThat(creados).hasSize(2);
        assertThat(creados).extracting(BloqueoResponse::hora).containsExactly("18:00", "19:00");
        verify(repository, times(2)).save(any(BloqueoPista.class));
        verify(cacheInvalidator).invalidate(FECHA);
        verify(auditRecorder).record(eq(BloqueoAuditActions.BLOQUEO_CREATED), eq(ADMIN_ID), any());
    }

    // conflicto — cualquier hora que solape una reserva activa rechaza TODA la petición (todo-o-nada)
    @Test
    @DisplayName("conflicto → rechaza TODO, no inserta nada ni invalida caché")
    void crear_conflict_rejects_everything() {
        // Reserva activa 19:00-20:00 → la franja 19:00 solicitada solapa.
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of(
                new ReservationOccupancy(H19, LocalTime.of(20, 0), 2)));

        assertThatThrownBy(() -> service.crear(FECHA, List.of(H18, H19), "Torneo", ADMIN_ID))
                .isInstanceOf(BloqueoConflictException.class)
                .extracting(e -> ((BloqueoConflictException) e).getConflictingSlots())
                .isEqualTo(List.of("19:00"));

        // Todo-o-nada: ni la franja libre (18:00) se inserta.
        verify(repository, never()).save(any());
        verify(cacheInvalidator, never()).invalidate(any());
        verify(auditRecorder, never()).record(any(), any(), any());
    }

    // idempotencia — una franja ya bloqueada no se vuelve a insertar
    @Test
    @DisplayName("idempotencia → franja ya bloqueada no se duplica")
    void crear_idempotent_over_existing_slot() {
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of());
        when(repository.existsByFechaAndHora(FECHA, H18)).thenReturn(true); // ya bloqueada
        when(repository.findByFechaOrderByHora(FECHA)).thenReturn(List.of(
                new BloqueoPista(FECHA, H18, "Torneo", ADMIN_ID)));

        List<BloqueoResponse> creados = service.crear(FECHA, List.of(H18), "Torneo", ADMIN_ID);

        assertThat(creados).hasSize(1);
        verify(repository, never()).save(any()); // no duplica
        verify(cacheInvalidator).invalidate(FECHA); // aun así refresca disponibilidad
    }

    // eliminar — borra, invalida caché de la fecha y audita BLOQUEO_DELETED
    @Test
    @DisplayName("eliminar → borra, invalida caché y audita BLOQUEO_DELETED")
    void eliminar_ok() {
        BloqueoPista bloqueo = new BloqueoPista(FECHA, H18, "Torneo", ADMIN_ID);
        when(repository.findById(7L)).thenReturn(java.util.Optional.of(bloqueo));

        service.eliminar(7L, ADMIN_ID);

        verify(repository).delete(bloqueo);
        verify(cacheInvalidator).invalidate(FECHA);
        verify(auditRecorder).record(eq(BloqueoAuditActions.BLOQUEO_DELETED), eq(ADMIN_ID), any());
    }

    // eliminar inexistente — no-op idempotente (204), sin invalidar ni auditar
    @Test
    @DisplayName("eliminar id inexistente → no-op idempotente")
    void eliminar_missing_is_noop() {
        when(repository.findById(404L)).thenReturn(java.util.Optional.empty());

        service.eliminar(404L, ADMIN_ID);

        verify(repository, never()).delete(any());
        verify(cacheInvalidator, never()).invalidate(any());
        verify(auditRecorder, never()).record(any(), any(), any());
    }
}
