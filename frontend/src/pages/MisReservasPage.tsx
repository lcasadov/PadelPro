// Grupo 6 — MisReservasPage (mockup 05).
// Lista las reservas del usuario (GET /api/reservas: owner o participante) con el
// estado de la reserva y el estado del pago asociado. Estado vacío con acceso a
// buscar disponibilidad.
import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getMisReservas, ReservaResponse } from '../services/reservasApi';
import { EstadoBadge } from '../components/EstadoBadge';
import { reservasPaths } from './reservasPaths';
import './pages.css';
import styles from './MisReservasPage.module.css';

export function MisReservasPage() {
  const { accessToken, isAuthenticated } = useAuth();

  const [reservas, setReservas] = useState<ReservaResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const page = await getMisReservas(accessToken);
      setReservas(page.data ?? []);
    } catch {
      setErrorMsg('No se pudieron cargar tus reservas. Inténtalo de nuevo.');
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    load();
  }, [load]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className="p-logo">
          <span className="p-dot" />
          PadelPro
        </div>
      </header>

      <div className={styles.titleBlock}>
        <h1>
          Tus<br />
          <em>reservas.</em>
        </h1>
      </div>

      {errorMsg && (
        <p className="p-error" role="alert">
          {errorMsg}
        </p>
      )}

      {loading ? (
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando reservas" />
        </div>
      ) : reservas.length === 0 ? (
        <div className={styles.empty}>
          <p className={styles.emptyText}>Todavía no tienes reservas.</p>
          <Link to={reservasPaths.disponibilidad} className="p-btn p-btn-primary">
            Buscar disponibilidad
          </Link>
        </div>
      ) : (
        <ul className={styles.list}>
          {reservas.map((r) => (
            <li key={r.id} className={styles.card}>
              <Link to={reservasPaths.detalle(r.id)} className={styles.cardLink}>
                <div className={styles.cardHead}>
                  <EstadoBadge kind="reserva" status={r.status} />
                  {r.pago && <EstadoBadge kind="pago" status={r.pago.status} />}
                </div>
                <div className={styles.cardBody}>
                  <div className={styles.timeBlock}>
                    <span className={styles.hora}>{r.startTime}</span>
                    <span className={styles.dur}>{r.durationMinutes} min</span>
                  </div>
                  <div className={styles.info}>
                    <span className={styles.fecha}>{r.reservationDate}</span>
                    <span className={styles.code}>{r.id}</span>
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
