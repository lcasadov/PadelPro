// partidas-unirse (Grupo 4.1/4.2) — PartidasAbiertasPage (mockup 21).
// Selector de fecha → GET /api/partidas?fecha=YYYY-MM-DD (reservas activas con
// plazas libres). Cada tarjeta muestra hora, duración, plazas libres y los
// participantes (nombre de display, sin PII). Seleccionar una partida navega a
// "Confirmar unión" con su reservaId y la fecha (la confirmación re-consulta el
// listado; no puede usar GET /reservas/{id} porque aún no es participante → 403).
import { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getPartidasAbiertas, PartidaAbierta } from '../services/reservasApi';
import { confirmarUnionPath, reservasPaths } from './reservasPaths';
import './pages.css';
import styles from './PartidasAbiertasPage.module.css';

/** Fecha de hoy en formato YYYY-MM-DD (input type=date). */
function todayISO(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Iniciales de display a partir del nombre (para el avatar). */
function iniciales(nombre: string): string {
  const parts = nombre.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** Copy de plazas libres ("falta 1 jugador" / "faltan N jugadores"). */
function plazasLabel(plazas: number): string {
  if (plazas <= 0) return 'Completa';
  return plazas === 1 ? 'falta 1 jugador' : `faltan ${plazas} jugadores`;
}

function formatPrecio(amount: number): string {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(amount);
}

export function PartidasAbiertasPage() {
  const { accessToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [fecha, setFecha] = useState<string>(todayISO());
  const [partidas, setPartidas] = useState<PartidaAbierta[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const data = await getPartidasAbiertas(accessToken, fecha);
      setPartidas(data);
    } catch {
      setPartidas([]);
      setErrorMsg('No se pudieron cargar las partidas. Inténtalo de nuevo.');
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

  function handleUnirse(reservaId: string) {
    navigate(confirmarUnionPath(reservaId, fecha));
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className="p-logo">
          <span className="p-dot" />
          PadelPro
        </div>
        <button
          type="button"
          className={styles.homeBtn}
          onClick={() => navigate(reservasPaths.home)}
        >
          Inicio
        </button>
      </header>

      <div className={styles.titleBlock}>
        <span className={styles.eyebrow}>Partidas</span>
        <h1>
          Partidas<br />
          <em>abiertas.</em>
        </h1>
      </div>

      <div className={styles.fieldGroup}>
        <label className={styles.fieldLabel} htmlFor="partidas-fecha">
          Fecha
        </label>
        <input
          id="partidas-fecha"
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
          <div className={styles.spinner} role="status" aria-label="Cargando partidas" />
        </div>
      ) : partidas.length === 0 ? (
        <p className={styles.empty}>No hay partidas abiertas para esta fecha.</p>
      ) : (
        <ul className={styles.list}>
          {partidas.map((partida) => (
            <li key={partida.reservaId} className={styles.card} data-partida>
              <div className={styles.cardHead}>
                <div className={styles.whenBlock}>
                  <span className={styles.hora}>{partida.startTime}</span>
                  <span className={styles.dur}>{partida.durationMinutes} min</span>
                </div>
                <span className={styles.plazas}>{plazasLabel(partida.plazasLibres)}</span>
              </div>

              <div className={styles.players}>
                {partida.participantes.map((p) => (
                  <span key={`${partida.reservaId}-${p.slotPosition}`} className={styles.avatar} title={p.nombre}>
                    {iniciales(p.nombre)}
                  </span>
                ))}
                {Array.from({ length: Math.max(0, partida.plazasLibres) }).map((_, i) => (
                  <span key={`${partida.reservaId}-empty-${i}`} className={`${styles.avatar} ${styles.avatarEmpty}`} aria-hidden="true">
                    +1
                  </span>
                ))}
              </div>

              <div className={styles.cardFooter}>
                <span className={styles.price}>
                  {formatPrecio(partida.priceTotal)} <small>total</small>
                </span>
                <button
                  type="button"
                  className={`p-btn p-btn-primary ${styles.joinBtn}`}
                  onClick={() => handleUnirse(partida.reservaId)}
                >
                  Unirme →
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
