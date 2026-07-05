// partidas-unirse (Grupo 4.3/4.4) — ConfirmarUnionPage (mockup 22).
// Confirma la unión a una partida abierta. Como el usuario aún NO participa, no
// puede usar GET /reservas/{id} (403): re-consulta el listado de partidas de la
// fecha (query `fecha`) y localiza la partida por `reservaId`.
//
// "Tu parte" = priceTotal ÷ (participantes + 1) es INFORMATIVO: la unión no crea
// pago ni cobra online (pago presencial; el cobro compartido se difiere a
// pagos-redsys). Unirse → POST /reservas/{id}/unirse (sin body). Se discrimina por
// `err.status`: 409 ya participante (sin checkout), 422 completa/estado no unible,
// 404 inexistente, 401 sesión caducada.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  getPartidasAbiertas,
  unirseReserva,
  isReservaApiError,
  PartidaAbierta,
} from '../services/reservasApi';
import { reservasPaths } from './reservasPaths';
import './pages.css';
import styles from './ConfirmarUnionPage.module.css';

function formatPrecio(amount: number): string {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(amount);
}

function iniciales(nombre: string): string {
  const parts = nombre.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** Estado de la acción de unirse (no del fetch inicial). */
type JoinState =
  | { kind: 'idle' }
  | { kind: 'joining' }
  | { kind: 'joined' }
  | { kind: 'already' } // 409: ya es participante (sin checkout)
  | { kind: 'full' } //    422: completa / estado no unible
  | { kind: 'notfound' } // 404
  | { kind: 'error'; message: string };

export function ConfirmarUnionPage() {
  const { accessToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [params] = useSearchParams();
  const fecha = params.get('fecha') ?? '';

  const [partida, setPartida] = useState<PartidaAbierta | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<boolean>(false);
  const [join, setJoin] = useState<JoinState>({ kind: 'idle' });

  const load = useCallback(async () => {
    if (!accessToken || !id) return;
    setLoading(true);
    setLoadError(false);
    try {
      const lista = await getPartidasAbiertas(accessToken, fecha);
      setPartida(lista.find((p) => p.reservaId === id) ?? null);
    } catch {
      setPartida(null);
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [accessToken, id, fecha]);

  useEffect(() => {
    load();
  }, [load]);

  // "Tu parte" informativa: total ÷ (participantes actuales + 1 = tú).
  const tuParte = useMemo(() => {
    if (!partida) return 0;
    return partida.priceTotal / (partida.participantes.length + 1);
  }, [partida]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  async function handleUnirse() {
    if (!accessToken || !id) return;
    setJoin({ kind: 'joining' });
    try {
      await unirseReserva(accessToken, id);
      setJoin({ kind: 'joined' });
    } catch (err) {
      if (isReservaApiError(err)) {
        if (err.code === 'AUTH_REQUIRED') {
          navigate('/login', { replace: true });
          return;
        }
        if (err.status === 409) return setJoin({ kind: 'already' });
        if (err.status === 422) return setJoin({ kind: 'full' });
        if (err.status === 404) return setJoin({ kind: 'notfound' });
      }
      setJoin({ kind: 'error', message: 'No se pudo completar la unión. Inténtalo de nuevo.' });
    }
  }

  if (loading) {
    return (
      <div className={styles.page}>
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando partida" />
        </div>
      </div>
    );
  }

  // Pantalla de éxito: unión confirmada (sin pago online).
  if (join.kind === 'joined') {
    return (
      <div className={styles.page}>
        <div className={styles.successCard} role="status">
          <div className={styles.successMark} aria-hidden="true">✓</div>
          <h1 className={styles.successTitle}>Te has unido a la partida</h1>
          <p className={styles.successText}>
            ¡Listo! Ya formas parte de esta partida. El pago se realiza de forma presencial
            en el club (sin pasarela online).
          </p>
          <button
            type="button"
            className="p-btn p-btn-dark"
            onClick={() => navigate(reservasPaths.mias)}
          >
            Ver mis reservas
          </button>
        </div>
      </div>
    );
  }

  // 409: ya es participante — mensaje claro, SIN checkout.
  if (join.kind === 'already') {
    return (
      <div className={styles.page}>
        <div className={styles.noticeCard} role="alert">
          <h1 className={styles.noticeTitle}>Ya estás en esta partida</h1>
          <p className={styles.noticeText}>
            Ya figuras como participante de esta partida, así que no hace falta que te unas
            de nuevo. Puedes verla en tus reservas.
          </p>
          <button
            type="button"
            className="p-btn p-btn-primary"
            onClick={() => navigate(reservasPaths.mias)}
          >
            Ver mis reservas
          </button>
        </div>
      </div>
    );
  }

  // 422: completa / estado no unible.
  if (join.kind === 'full') {
    return (
      <div className={styles.page}>
        <div className={styles.noticeCard} role="alert">
          <h1 className={styles.noticeTitle}>Partida completa</h1>
          <p className={styles.noticeText}>
            Esta partida ya no admite nuevos jugadores: se ha completado o ya no está
            disponible. Vuelve al listado para elegir otra.
          </p>
          <button
            type="button"
            className="p-btn p-btn-primary"
            onClick={() => navigate(reservasPaths.partidas)}
          >
            Ver otras partidas
          </button>
        </div>
      </div>
    );
  }

  // La partida no aparece en el listado (completada/cancelada entre pantallas), 404
  // al unirse, o fallo de carga: aviso neutro sin exponer datos.
  if (!partida || loadError || join.kind === 'notfound') {
    return (
      <div className={styles.page}>
        <div className={styles.noticeCard} role="alert">
          <h1 className={styles.noticeTitle}>Partida ya no disponible</h1>
          <p className={styles.noticeText}>
            Esta partida ya no está disponible. Puede que se haya completado o cancelado.
            Vuelve al listado para ver las partidas abiertas.
          </p>
          <button
            type="button"
            className="p-btn p-btn-primary"
            onClick={() => navigate(reservasPaths.partidas)}
          >
            Ver partidas abiertas
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <button
          type="button"
          className={styles.backBtn}
          onClick={() => navigate(reservasPaths.partidas)}
          aria-label="Volver a partidas"
        >
          ←
        </button>
        <span className={styles.headStatus}>
          <span className={styles.headDot} aria-hidden="true" /> Partida abierta · {fecha}
        </span>
        <h1 className={styles.headTitle}>
          {partida.plazasLibres === 1 ? (
            <>Falta<br /><em>1 jugador.</em></>
          ) : (
            <>Faltan<br /><em>{partida.plazasLibres} jugadores.</em></>
          )}
        </h1>
        <div className={styles.headTime}>
          {partida.startTime} · {partida.durationMinutes} min
        </div>
        <div className={styles.headCode}>{partida.reservaId}</div>
      </header>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>
          Jugadores · {partida.participantes.length} confirmados
        </h3>
        <ul className={styles.players}>
          {partida.participantes.map((p) => (
            <li key={p.slotPosition} className={styles.player}>
              <span className={styles.avatar}>{iniciales(p.nombre)}</span>
              {p.nombre}
              {p.owner && <span className={styles.hostTag}> · Anfitrión</span>}
            </li>
          ))}
          <li className={`${styles.player} ${styles.playerEmpty}`}>
            <span className={`${styles.avatar} ${styles.avatarEmpty}`}>?</span>
            Tú
          </li>
        </ul>
      </section>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Detalles</h3>
        <div className={styles.grid}>
          <div className={styles.pill}>
            <span className={styles.pillLab}>Horario</span>
            <span className={styles.pillVal}>{partida.startTime}</span>
          </div>
          <div className={styles.pill}>
            <span className={styles.pillLab}>Duración</span>
            <span className={styles.pillVal}>{partida.durationMinutes} min</span>
          </div>
          <div className={styles.pill}>
            <span className={styles.pillLab}>Tu parte</span>
            <span className={styles.pillVal} data-testid="tu-parte">{formatPrecio(tuParte)}</span>
          </div>
          <div className={styles.pill}>
            <span className={styles.pillLab}>Total partida</span>
            <span className={styles.pillVal}>{formatPrecio(partida.priceTotal)}</span>
          </div>
        </div>
      </section>

      <div className={styles.infoBox}>
        <b>Importe informativo.</b> Tu parte ({formatPrecio(tuParte)}) es orientativa: el
        pago se realiza de forma <b>presencial</b> en el club. No se cobra online al unirte.
      </div>

      {join.kind === 'error' && (
        <p className="p-error" role="alert">
          {join.message}
        </p>
      )}

      <div className={styles.footer}>
        <button
          type="button"
          className={`p-btn p-btn-primary ${styles.joinBtn}`}
          onClick={handleUnirse}
          disabled={join.kind === 'joining'}
        >
          {join.kind === 'joining'
            ? 'Uniéndote…'
            : `Unirme · ${formatPrecio(tuParte)} →`}
        </button>
      </div>
    </div>
  );
}
