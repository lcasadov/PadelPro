// pagos-redsys-online (Grupo 6, D6) — PagoConfirmadoPage (mockup 12).
// Página de retorno del TPV (Redsys UrlOK/UrlKO). NO confía en los parámetros de
// resultado de la URL de Redsys: el estado real del pago lo fija el webhook
// (server-to-server, fuente de verdad). Aquí se consulta `GET /api/pagos` y se
// localiza el pago de la reserva (por `reservaId`/`pagoId` de la query) para
// mostrar el estado real: PAID / FAILED / "procesando" (si el webhook aún no ha
// llegado, con opción de refrescar).
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getPagos, type PagoHistorial } from '../services/pagosApi';
import { reservasPaths } from './reservasPaths';
import './pages.css';
import styles from './PagoConfirmadoPage.module.css';

function formatPrecio(amount: number): string {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(amount);
}

/** Referencia Redsys a mostrar: transactionId (Ds_AuthorisationCode) o el order id;
 *  fallback al id del pago si el backend no envía ninguna. */
function referenciaRedsys(pago: PagoHistorial): string {
  return pago.transactionId ?? pago.redsysOrderId ?? pago.id;
}

export function PagoConfirmadoPage() {
  const { accessToken } = useAuth();
  const [searchParams] = useSearchParams();
  const reservaId = searchParams.get('reservaId');
  const pagoId = searchParams.get('pagoId');

  const [pagos, setPagos] = useState<PagoHistorial[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const lista = await getPagos(accessToken);
      setPagos(lista);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    load();
  }, [load]);

  const pago = useMemo(() => {
    if (pagoId) return pagos.find((p) => p.id === pagoId) ?? null;
    if (reservaId) return pagos.find((p) => p.reservaId === reservaId) ?? null;
    return null;
  }, [pagos, pagoId, reservaId]);

  if (loading) {
    return (
      <div className={styles.page}>
        <div className={styles.spinner} role="status" aria-label="Consultando el estado del pago" />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <h1 className={styles.title}>No se pudo comprobar el pago</h1>
          <p className={styles.sub}>
            No hemos podido consultar el estado de tu pago. Inténtalo de nuevo en unos
            instantes.
          </p>
          <div className={styles.actions}>
            <button type="button" className="p-btn p-btn-primary" onClick={load}>
              Reintentar
            </button>
            <Link to={reservasPaths.mias} className="p-btn p-btn-outline">
              Ver mis reservas →
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // No se encuentra el pago (webhook aún no ha creado/actualizado, o falta el id).
  const status = pago?.status;
  const esPagado = status === 'PAID';
  const esFallido = status === 'FAILED' || status === 'CANCELLED';

  if (esPagado && pago) {
    return (
      <div className={`${styles.page} ${styles.pageOk}`}>
        <div className={styles.successCard}>
          <div className={styles.successIco} aria-hidden="true">
            ✓
          </div>
          <h1 className={styles.successTitle}>
            ¡Pista <em>reservada!</em>
          </h1>
          <p className={styles.successSub}>Tu pago se ha confirmado correctamente.</p>

          <dl className={styles.detailCard}>
            <div className={styles.detailRow}>
              <dt>Reserva</dt>
              <dd>{pago.reservaId}</dd>
            </div>
            <div className={styles.detailRow}>
              <dt>Referencia Redsys</dt>
              <dd>{referenciaRedsys(pago)}</dd>
            </div>
            <div className={styles.detailRow}>
              <dt>Importe</dt>
              <dd>{formatPrecio(pago.amount)}</dd>
            </div>
            <div className={styles.detailRow}>
              <dt>Pago</dt>
              <dd className={styles.confirmed}>✓ Confirmado</dd>
            </div>
          </dl>

          <div className={styles.actions}>
            <Link to={reservasPaths.detalle(pago.reservaId)} className="p-btn p-btn-primary">
              Ver reserva →
            </Link>
            <Link to={reservasPaths.mias} className="p-btn p-btn-outline">
              Mis reservas
            </Link>
          </div>
        </div>
      </div>
    );
  }

  if (esFallido && pago) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <div className={styles.failIco} aria-hidden="true">
            ✕
          </div>
          <h1 className={styles.title}>El pago no se ha completado</h1>
          <p className={styles.sub}>
            No se ha realizado ningún cargo. Puedes volver a intentar el pago desde tus
            reservas.
          </p>
          <div className={styles.actions}>
            <Link to={reservasPaths.mias} className="p-btn p-btn-primary">
              Volver a mis reservas
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // enProceso — el webhook puede tardar unos segundos en confirmar.
  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.spinner} role="status" aria-label="Procesando el pago" />
        <h1 className={styles.title}>Estamos procesando tu pago</h1>
        <p className={styles.sub}>
          La confirmación puede tardar unos segundos. Si has completado el pago, pulsa
          «Actualizar» para comprobar el estado.
        </p>
        <div className={styles.actions}>
          <button type="button" className="p-btn p-btn-primary" onClick={load}>
            Actualizar
          </button>
          <Link to={reservasPaths.mias} className="p-btn p-btn-outline">
            Ver mis reservas →
          </Link>
        </div>
      </div>
    </div>
  );
}
