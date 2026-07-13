package com.padelpro.bloqueos.infrastructure.persistence;

import com.padelpro.bloqueos.domain.model.BloqueoPista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Spring Data JPA repository for {@link BloqueoPista} (change bloqueos-pista-eventos).
 *
 * <p>Backed by {@code idx_bloqueo_fecha} for date lookups and by the unique {@code (fecha, hora)}
 * constraint for the idempotent block check.
 */
@Repository
public interface BloqueoJpaRepository extends JpaRepository<BloqueoPista, Long> {

    /** All blocks of a date, ordered by hour (ADMIN listing). */
    List<BloqueoPista> findByFechaOrderByHora(LocalDate fecha);

    /** Whether the slot {@code (fecha, hora)} is already blocked (idempotency check). */
    boolean existsByFechaAndHora(LocalDate fecha, LocalTime hora);

    /** Blocked slot-start hours of a date, projected to avoid loading full entities. */
    @Query("SELECT b.hora FROM BloqueoPista b WHERE b.fecha = :fecha")
    List<LocalTime> findHorasByFecha(@Param("fecha") LocalDate fecha);
}
