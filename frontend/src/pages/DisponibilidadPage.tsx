// Grupo 4 — DisponibilidadPage (mockup 03).
// Selector de fecha → tramos de GET /api/reservas/disponibles?fecha=YYYY-MM-DD.
// La acción "Reservar" se ofrece SOLO en tramos con `creable === true` (D7).
// Fail-safe: cualquier tramo sin `creable === true` (undefined/false) NO ofrece
// crear. Estado vacío neutro (no revela mantenimiento, RN de spec). Seleccionar
// un tramo creable navega a "Confirmar" pasando fecha + hora + duración (D3).
import { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getDisponibilidad, Tramo } from '../services/reservasApi';
import { confirmarReservaPath } from './reservasPaths';
import './pages.css';
import styles from './DisponibilidadPage.module.css';

/** Fecha de hoy en formato YYYY-MM-DD (input type=date). */
function todayISO(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function DisponibilidadPage() {
  const { accessToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [fecha, setFecha] = useState<string>(todayISO());
  const [tramos, setTramos] = useState<Tramo[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const data = await getDisponibilidad(accessToken, fecha);
      setTramos(data.tramosDisponibles ?? []);
    } catch {
      setTramos([]);
      setErrorMsg('No se pudo cargar la disponibilidad. Inténtalo de nuevo.');
    } finally {
      setLoading(false);
    }
  }, [accessToken, fecha]);

  useEffect(() => {
    load();
  }, [load]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  function handleReservar(tramo: Tramo) {
    navigate(confirmarReservaPath(fecha, tramo.horaInicio, tramo.duracionMinutos));
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
        <span className={styles.eyebrow}>Reservas</span>
        <h1>
          Buscar<br />
          <em>pista.</em>
        </h1>
      </div>

      <div className={styles.fieldGroup}>
        <label className={styles.fieldLabel} htmlFor="disp-fecha">
          Fecha
        </label>
        <input
          id="disp-fecha"
          className={styles.dateInput}
          type="date"
          value={fecha}
          onChange={(e) => setFecha(e.target.value)}
        />
      </div>

      {errorMsg && (
        <p className="p-error" role="alert">
          {errorMsg}
        </p>
      )}

      {loading ? (
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando disponibilidad" />
        </div>
      ) : tramos.length === 0 ? (
        <p className={styles.empty}>No hay tramos disponibles para esta fecha.</p>
      ) : (
        <ul className={styles.tramoList}>
          {tramos.map((tramo) => {
            const creable = tramo.creable === true; // fail-safe (D7)
            return (
              <li
                key={`${tramo.horaInicio}-${tramo.duracionMinutos}`}
                className={styles.tramoItem}
                data-tramo
              >
                <div className={styles.tramoInfo}>
                  <span className={styles.tramoHora}>{tramo.horaInicio}</span>
                  <span className={styles.tramoDur}>{tramo.duracionMinutos} min</span>
                </div>
                {creable ? (
                  <button
                    type="button"
                    className={`p-btn p-btn-primary ${styles.reservarBtn}`}
                    onClick={() => handleReservar(tramo)}
                  >
                    Reservar {tramo.horaInicio}
                  </button>
                ) : (
                  <span className={styles.noDisponible}>No disponible</span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
