// pagos-simulador-gestion (D3) — AdminPagosPage (panel de cobros, /admin/pagos).
// Lista TODAS las reservas/pagos del club (GET /api/admin/pagos) con titular,
// importe (€), estado y fecha. Filtro Todas / Pagadas / Pendientes. En las
// pendientes, "Marcar como pagada (efectivo)" → POST /api/admin/pagos/{reservaId}/efectivo
// y refresca la lista.
// Autorización real en backend (/api/admin/** = ROLE_ADMIN); AdminRoute es defensa
// en profundidad (D6).
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getAdminPagos, marcarPagoEfectivo, type AdminPago } from '../services/adminPagosApi';
import { EstadoBadge } from '../components/EstadoBadge';
import './pages.css';
import styles from './AdminPagosPage.module.css';

type Filtro = 'ALL' | 'PAID' | 'PENDING';

const FILTROS: { value: Filtro; label: string }[] = [
  { value: 'ALL', label: 'Todas' },
  { value: 'PAID', label: 'Pagadas' },
  { value: 'PENDING', label: 'Pendientes' },
];

function formatPrecio(amount: number): string {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(amount);
}

/** Fecha a mostrar: la del cobro si existe, si no la de creación. Se recorta a la
 *  parte de fecha (YYYY-MM-DD) para una lectura compacta. */
function formatFecha(pago: AdminPago): string {
  const raw = pago.paidAt ?? pago.createdAt;
  if (!raw) return '—';
  return raw.slice(0, 10);
}

/** Un pago cuenta como "pendiente" (marcable en efectivo) si no está PAID ni en un
 *  estado terminal de fallo/cancelación/reembolso. */
function esPendiente(pago: AdminPago): boolean {
  return pago.status === 'PENDING' || pago.status === 'IN_PROGRESS';
}

export function AdminPagosPage() {
  const { accessToken } = useAuth();

  const [pagos, setPagos] = useState<AdminPago[]>([]);
  const [filtro, setFiltro] = useState<Filtro>('ALL');
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const lista = await getAdminPagos(accessToken);
      setPagos(lista);
    } catch {
      setErrorMsg('No se pudo cargar la lista de pagos. Inténtalo de nuevo.');
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    load();
  }, [load]);

  const visibles = useMemo(() => {
    if (filtro === 'PAID') return pagos.filter((p) => p.status === 'PAID');
    if (filtro === 'PENDING') return pagos.filter(esPendiente);
    return pagos;
  }, [pagos, filtro]);

  async function handleMarcarEfectivo(reservaId: string) {
    if (!accessToken) return;
    setBusyId(reservaId);
    setErrorMsg(null);
    try {
      await marcarPagoEfectivo(accessToken, reservaId);
      await load();
    } catch {
      setErrorMsg('No se pudo marcar el pago como pagado. Inténtalo de nuevo.');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className={styles.adminPage}>
      <header className={styles.header}>
        <Link to="/home" className="p-logo" aria-label="Volver al inicio">
          <span className="p-dot" />
          PadelPro
        </Link>
        <span className={styles.badge}>Admin</span>
      </header>

      <div className={styles.titleBlock}>
        <span className={styles.eyebrow}>Gestión de cobros</span>
        <h1>
          Cobros<br />
          <em>del club.</em>
        </h1>
      </div>

      <div className={styles.filterRow} role="group" aria-label="Filtrar pagos por estado">
        {FILTROS.map((f) => (
          <button
            key={f.value}
            type="button"
            className={`${styles.filterBtn} ${filtro === f.value ? styles.filterActive : ''}`}
            aria-pressed={filtro === f.value}
            onClick={() => setFiltro(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {errorMsg && (
        <p className="p-error" role="alert">
          {errorMsg}
        </p>
      )}

      {loading ? (
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando pagos" />
        </div>
      ) : visibles.length === 0 ? (
        <p className={styles.empty}>No hay pagos para el filtro seleccionado.</p>
      ) : (
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">Titular / Reserva</th>
              <th scope="col">Importe</th>
              <th scope="col">Estado</th>
              <th scope="col">Fecha</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {visibles.map((p) => (
              <tr key={p.id ?? p.reservaId}>
                <td>
                  <div className={styles.titular}>{p.titular ?? '—'}</div>
                  <div className={styles.reservaId}>{p.reservaId}</div>
                </td>
                <td className={styles.importe}>{formatPrecio(p.amount)}</td>
                <td>
                  <EstadoBadge kind="pago" status={p.status} />
                </td>
                <td className={styles.fecha}>{formatFecha(p)}</td>
                <td>
                  {esPendiente(p) ? (
                    <button
                      type="button"
                      className={styles.actionBtn}
                      onClick={() => handleMarcarEfectivo(p.reservaId)}
                      disabled={busyId === p.reservaId}
                    >
                      {busyId === p.reservaId ? 'Marcando…' : 'Marcar como pagada (efectivo)'}
                    </button>
                  ) : (
                    <span className={styles.noAction}>—</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
