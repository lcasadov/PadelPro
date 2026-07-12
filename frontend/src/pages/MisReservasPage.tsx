// Grupo 6 / reservas-ui-jugador-fixes (Grupo 5, D6) — MisReservasPage (mockup 05).
// Lista las reservas del usuario (GET /api/reservas) con el estado de la reserva y
// del pago. Por cada tarjeta, sin entrar al detalle:
//   - Cancelar inline (DELETE /api/reservas/{id}), solo si owner y estado cancelable;
//     refresca la lista tras cancelar.
//   - Método de pago: "pago en diferido" (cobro presencial, sin llamada) y "pagar
//     ahora" DESHABILITADO/informativo hasta el change `pagos-redsys`.
// Las acciones no aplicables por estado se ocultan. Control de "volver a Inicio".
import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  getMisReservas,
  cancelarReserva,
  isReservaApiError,
  ReservaResponse,
  ReservationStatus,
} from '../services/reservasApi';
import { iniciarPago } from '../services/pagosApi';
import { EstadoBadge } from '../components/EstadoBadge';
import { reservasPaths, simuladorPath } from './reservasPaths';
import './pages.css';
import styles from './MisReservasPage.module.css';

/** Estados en los que una reserva admite cancelación desde la UI. La barrera real
 *  la impone el backend (422 INVALID_STATE_TRANSITION / CANCELLATION_DEADLINE_PASSED). */
const ESTADOS_CANCELABLES: ReservationStatus[] = ['PENDING_CONFIRMATION', 'CONFIRMED'];

/** Bandera provisional (pagos-simulador-gestion, D1): mientras Redsys no está
 *  configurado, "pagar ahora" enruta al simulador. El flujo Redsys real (iniciarPago
 *  → checkout firmado) queda intacto detrás de esta bandera para reactivarlo sin
 *  reescribir nada cuando haya credenciales. */
const USE_REDSYS: boolean = false;

export function MisReservasPage() {
  const { accessToken, isAuthenticated, userId, loadUserId } = useAuth();
  const navigate = useNavigate();

  const [reservas, setReservas] = useState<ReservaResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Estado por-tarjeta de las acciones inline.
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState<{ id: string; msg: string } | null>(null);
  const [diferidoId, setDiferidoId] = useState<string | null>(null);
  const [pagandoId, setPagandoId] = useState<string | null>(null);
  const [pagoError, setPagoError] = useState<{ id: string; msg: string } | null>(null);

  // Id del usuario (D8) para decidir la propiedad de cada reserva (botón cancelar).
  useEffect(() => {
    void loadUserId();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const lista = await getMisReservas(accessToken);
      setReservas(lista);
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

  // "Pagar ahora": mientras Redsys no está configurado (D1) enruta al simulador
  // provisional pasando reservaId + importe. Si USE_REDSYS se reactiva, vuelve al
  // flujo real (iniciarPago → checkout firmado). Solo owner con pago PENDING.
  async function handlePagar(id: string, importe?: number) {
    if (!accessToken) return;
    if (!USE_REDSYS) {
      navigate(simuladorPath(id, importe), { state: { reservaId: id, importe } });
      return;
    }
    setPagandoId(id);
    setPagoError(null);
    try {
      const pago = await iniciarPago(accessToken, id);
      navigate(reservasPaths.checkout, { state: { pago } });
    } catch (err) {
      const msg =
        isReservaApiError(err) && err.code === 'CONFLICT'
          ? 'Este pago ya está en curso o completado.'
          : 'No se pudo iniciar el pago. Inténtalo de nuevo.';
      setPagoError({ id, msg });
      setPagandoId(null);
    }
  }

  async function handleCancel(id: string) {
    if (!accessToken) return;
    setCancellingId(id);
    setCancelError(null);
    try {
      await cancelarReserva(accessToken, id);
      setConfirmingId(null);
      await load();
    } catch (err) {
      const msg =
        isReservaApiError(err) && err.code === 'CANCELLATION_DEADLINE_PASSED'
          ? 'La cancelación está fuera de plazo; no aplica reembolso (política del club).'
          : 'No se pudo cancelar la reserva. Inténtalo de nuevo.';
      setCancelError({ id, msg });
    } finally {
      setCancellingId(null);
    }
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
          {reservas.map((r) => {
            const isOwner = r.ownerId != null && userId != null && r.ownerId === userId;
            const canCancel = isOwner && ESTADOS_CANCELABLES.includes(r.status);
            const pagoPendiente =
              r.pago != null && (r.pago.status === 'PENDING' || r.pago.status === 'IN_PROGRESS');
            const mostrarPago = pagoPendiente && r.status !== 'CANCELLED';
            // "Pagar ahora": solo el owner y solo con pago PENDING (RN-AUTH-04).
            const puedePagar = isOwner && r.pago?.status === 'PENDING' && r.status !== 'CANCELLED';

            return (
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

                {/* Acciones inline (fuera del Link para no anidar interactivos) */}
                {(canCancel || mostrarPago) && (
                  <div className={styles.actions}>
                    {/* Método de pago (D6): diferido operativo, "pagar ahora" deshabilitado */}
                    {mostrarPago && (
                      <div className={styles.pagoBlock}>
                        <span className={styles.actionsLabel}>Método de pago</span>
                        <div className={styles.pagoBtns}>
                          <button
                            type="button"
                            className={styles.pagoBtn}
                            onClick={() => setDiferidoId(r.id)}
                          >
                            Pago en diferido
                          </button>
                          {puedePagar && (
                            <button
                              type="button"
                              className={styles.pagarBtn}
                              onClick={() => handlePagar(r.id, r.pago?.amount ?? r.priceTotal)}
                              disabled={pagandoId === r.id}
                            >
                              {pagandoId === r.id ? 'Iniciando…' : 'Pagar ahora'}
                            </button>
                          )}
                        </div>
                        {diferidoId === r.id && (
                          <p className={styles.pagoInfo} role="status">
                            El cobro se realizará de forma presencial en el club. La reserva
                            seguirá figurando como <strong>Pendiente</strong> hasta que se
                            confirme el pago.
                          </p>
                        )}
                        {pagoError?.id === r.id && (
                          <p className="p-error" role="alert">
                            {pagoError.msg}
                          </p>
                        )}
                      </div>
                    )}

                    {/* Cancelar inline (D6) */}
                    {canCancel && confirmingId !== r.id && (
                      <button
                        type="button"
                        className={styles.cancelBtn}
                        onClick={() => {
                          setCancelError(null);
                          setConfirmingId(r.id);
                        }}
                      >
                        Cancelar
                      </button>
                    )}

                    {canCancel && confirmingId === r.id && (
                      <div className={styles.confirmRow}>
                        <span className={styles.confirmText}>¿Cancelar esta reserva?</span>
                        <div className={styles.confirmBtns}>
                          <button
                            type="button"
                            className={styles.volverBtn}
                            onClick={() => setConfirmingId(null)}
                            disabled={cancellingId === r.id}
                          >
                            Volver
                          </button>
                          <button
                            type="button"
                            className={styles.cancelBtn}
                            onClick={() => handleCancel(r.id)}
                            disabled={cancellingId === r.id}
                          >
                            {cancellingId === r.id ? 'Cancelando…' : 'Confirmar cancelación'}
                          </button>
                        </div>
                      </div>
                    )}

                    {cancelError?.id === r.id && (
                      <p className="p-error" role="alert">
                        {cancelError.msg}
                      </p>
                    )}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
