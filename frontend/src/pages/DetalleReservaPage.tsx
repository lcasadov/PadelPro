// Grupo 7 — DetalleReservaPage (mockup 13).
// Detalle desde GET /api/reservas/{id}. El botón cancelar solo se muestra cuando
// el usuario autenticado es el owner (userId === ownerId, cacheado en AuthContext
// vía loadUserId, D8) y el estado es cancelable. Cancelar → DELETE /api/reservas/{id}.
// Se respetan 403 (ajena, RN-RGPD-03) y 404 (inexistente) sin exponer datos; el
// 422 CANCELLATION_DEADLINE_PASSED informa de fuera de plazo y no reembolso (RN-RES-04).
import { useCallback, useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  getReserva,
  cancelarReserva,
  isReservaApiError,
  ReservaResponse,
  ReservationStatus,
} from '../services/reservasApi';
import { EstadoBadge } from '../components/EstadoBadge';
import './pages.css';
import styles from './DetalleReservaPage.module.css';

/** Estados en los que una reserva admite cancelación desde la UI. La barrera real
 *  la impone el backend (422 INVALID_STATE_TRANSITION / CANCELLATION_DEADLINE_PASSED). */
const ESTADOS_CANCELABLES: ReservationStatus[] = ['PENDING_CONFIRMATION', 'CONFIRMED'];

type LoadError = 'forbidden' | 'notfound' | 'generic';

function formatPrecio(amount: number): string {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(amount);
}

export function DetalleReservaPage() {
  const { accessToken, userId, loadUserId, isAuthenticated } = useAuth();
  const { id } = useParams<{ id: string }>();

  const [reserva, setReserva] = useState<ReservaResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<LoadError | null>(null);

  const [confirming, setConfirming] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  // Asegura el id de usuario (D8) para decidir la visibilidad del botón cancelar.
  useEffect(() => {
    void loadUserId();
    // Solo al montar: loadUserId es idempotente (no-op si ya está cargado).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const load = useCallback(async () => {
    if (!accessToken || !id) return;
    setLoading(true);
    setLoadError(null);
    try {
      const data = await getReserva(accessToken, id);
      setReserva(data);
    } catch (err) {
      if (isReservaApiError(err) && err.code === 'FORBIDDEN') setLoadError('forbidden');
      else if (isReservaApiError(err) && err.code === 'NOT_FOUND') setLoadError('notfound');
      else setLoadError('generic');
    } finally {
      setLoading(false);
    }
  }, [accessToken, id]);

  useEffect(() => {
    load();
  }, [load]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const isOwner =
    reserva?.ownerId != null && userId != null && reserva.ownerId === userId;
  const canCancel = Boolean(
    reserva && isOwner && ESTADOS_CANCELABLES.includes(reserva.status)
  );

  async function handleConfirmCancel() {
    if (!accessToken || !id) return;
    setCancelling(true);
    setCancelError(null);
    try {
      await cancelarReserva(accessToken, id);
      setReserva((prev) => (prev ? { ...prev, status: 'CANCELLED' } : prev));
      setConfirming(false);
    } catch (err) {
      if (isReservaApiError(err) && err.code === 'CANCELLATION_DEADLINE_PASSED') {
        setCancelError(
          'La cancelación está fuera de plazo; no aplica reembolso (política del club).'
        );
      } else {
        setCancelError('No se pudo cancelar la reserva. Inténtalo de nuevo.');
      }
    } finally {
      setCancelling(false);
    }
  }

  if (loading) {
    return (
      <div className={styles.page}>
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando reserva" />
        </div>
      </div>
    );
  }

  // Errores de carga — sin exponer datos de la reserva (RN-RGPD-03).
  if (loadError) {
    const msg =
      loadError === 'forbidden'
        ? 'Acceso denegado: no tienes acceso a esta reserva.'
        : loadError === 'notfound'
          ? 'Reserva no encontrada.'
          : 'No se pudo cargar la reserva. Inténtalo de nuevo.';
    return (
      <div className={styles.page}>
        <p className="p-error" role="alert">
          {msg}
        </p>
      </div>
    );
  }

  if (!reserva) return null;

  return (
    <div className={styles.page}>
      <header className={styles.hero}>
        <div className={styles.heroBadges}>
          <EstadoBadge kind="reserva" status={reserva.status} />
          {reserva.pago && <EstadoBadge kind="pago" status={reserva.pago.status} />}
        </div>
        <div className={styles.heroTime}>{reserva.startTime}</div>
        <div className={styles.heroDate}>{reserva.reservationDate}</div>
        <div className={styles.heroCode}>{reserva.id}</div>
      </header>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Horario</h3>
        <div className={styles.grid}>
          <div className={styles.pill}>
            <span className={styles.pillLab}>Franja</span>
            <span className={styles.pillVal}>
              {`${reserva.startTime}${reserva.endTime ? ` — ${reserva.endTime}` : ''}`}
            </span>
          </div>
          <div className={styles.pill}>
            <span className={styles.pillLab}>Duración</span>
            <span className={styles.pillVal}>{reserva.durationMinutes} min</span>
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Jugadores</h3>
        <ul className={styles.players}>
          {reserva.participants.map((p, index) => {
            const key = p.userId ?? p.slotPosition ?? index;
            if (p.owner) {
              const esYo = userId != null && p.userId === userId;
              return (
                <li key={key} className={styles.player}>
                  {`Organizador${esYo ? ' · Tú' : ''}`}
                </li>
              );
            }
            return (
              <li key={key} className={styles.player}>
                {p.externalName}
              </li>
            );
          })}
        </ul>
      </section>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Pago</h3>
        <div className={styles.grid}>
          <div className={styles.pill}>
            <span className={styles.pillLab}>Importe</span>
            <span className={styles.pillVal}>{formatPrecio(reserva.priceTotal)}</span>
          </div>
        </div>
      </section>

      {cancelError && (
        <p className="p-error" role="alert">
          {cancelError}
        </p>
      )}

      {/* Cancelar: visible solo para el owner y estados cancelables (D8) */}
      {canCancel && !confirming && (
        <button
          type="button"
          className={`p-btn ${styles.cancelBtn}`}
          onClick={() => {
            setCancelError(null);
            setConfirming(true);
          }}
        >
          Cancelar reserva
        </button>
      )}

      {canCancel && confirming && (
        <div className={styles.confirmPanel} role="dialog" aria-label="Confirmar cancelación">
          <p className={styles.confirmPolicy}>
            <b>Política de cancelación (RN-04)</b>
            <br />
            Cancelación gratuita hasta 24h antes (100% reembolso). Con menos de 24h no
            es reembolsable.
          </p>
          <div className={styles.confirmActions}>
            <button
              type="button"
              className="p-btn p-btn-outline"
              onClick={() => setConfirming(false)}
              disabled={cancelling}
            >
              Volver
            </button>
            <button
              type="button"
              className={`p-btn ${styles.cancelBtn}`}
              onClick={handleConfirmCancel}
              disabled={cancelling}
            >
              {cancelling ? 'Cancelando…' : 'Confirmar cancelación'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
