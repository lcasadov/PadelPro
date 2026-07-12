// 5.1 + 5.3 — AdminDashboardPage — dashboard de administración del club
// (capability administracion-club, D5). Bajo AdminRoute (solo ADMIN — RN-ADM-01;
// la autorización efectiva la impone el backend en /api/admin/**).
// Selector de rango de fechas (default = mes actual: día 1 → hoy) y tarjetas de
// ocupación (RN-ADM-02) e ingresos con desglose REDSYS/CASH (RN-ADM-03), más un
// botón de exportación CSV del rango (RN-ADM-04).
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  getOcupacion,
  getIngresos,
  exportarCsv,
  OcupacionResponse,
  IngresosResponse,
  RangoFechas,
} from '../services/dashboardApi';
import './pages.css';
import styles from './AdminDashboardPage.module.css';

// Formateadores locales (es-ES): euros para importes y porcentaje para ocupación.
const eur = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });
const pct = new Intl.NumberFormat('es-ES', {
  style: 'percent',
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

/** Formatea una fecha local como YYYY-MM-DD sin desplazamiento por zona horaria. */
function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Rango por defecto: primer día del mes actual → hoy. */
function defaultRango(): RangoFechas {
  const now = new Date();
  const first = new Date(now.getFullYear(), now.getMonth(), 1);
  return { fechaInicio: toIsoDate(first), fechaFin: toIsoDate(now) };
}

export function AdminDashboardPage() {
  const { accessToken } = useAuth();

  const [rango, setRango] = useState<RangoFechas>(defaultRango);
  const [ocupacion, setOcupacion] = useState<OcupacionResponse | null>(null);
  const [ingresos, setIngresos] = useState<IngresosResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const [ocu, ing] = await Promise.all([
        getOcupacion(accessToken, rango),
        getIngresos(accessToken, rango),
      ]);
      setOcupacion(ocu);
      setIngresos(ing);
    } catch {
      setOcupacion(null);
      setIngresos(null);
      setErrorMsg('No se pudieron cargar las métricas del club. Inténtalo de nuevo.');
    } finally {
      setLoading(false);
    }
  }, [accessToken, rango]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleExport() {
    if (!accessToken) return;
    setExporting(true);
    setExportError(null);
    try {
      await exportarCsv(accessToken, rango);
    } catch {
      setExportError('No se pudo generar el informe CSV. Inténtalo de nuevo.');
    } finally {
      setExporting(false);
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
        <span className={styles.eyebrow}>Rendimiento</span>
        <h1>
          Dashboard<br />
          <em>del club.</em>
        </h1>
      </div>

      {/* Selector de rango (fechaInicio/fechaFin) */}
      <form
        className={styles.rangeRow}
        onSubmit={(e) => {
          e.preventDefault();
          load();
        }}
      >
        <div className={styles.field}>
          <label className={styles.fieldLabel} htmlFor="dash-inicio">
            Desde
          </label>
          <input
            id="dash-inicio"
            type="date"
            className={styles.dateInput}
            value={rango.fechaInicio}
            max={rango.fechaFin}
            onChange={(e) => setRango((r) => ({ ...r, fechaInicio: e.target.value }))}
          />
        </div>
        <div className={styles.field}>
          <label className={styles.fieldLabel} htmlFor="dash-fin">
            Hasta
          </label>
          <input
            id="dash-fin"
            type="date"
            className={styles.dateInput}
            value={rango.fechaFin}
            min={rango.fechaInicio}
            onChange={(e) => setRango((r) => ({ ...r, fechaFin: e.target.value }))}
          />
        </div>
        <button type="submit" className={`p-btn p-btn-outline ${styles.applyBtn}`}>
          Aplicar
        </button>
      </form>

      {errorMsg && (
        <p className="p-error" role="alert">
          {errorMsg}
        </p>
      )}

      {loading ? (
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando métricas" />
        </div>
      ) : (
        <div className={styles.cards}>
          {/* Tarjeta de ocupación (RN-ADM-02) */}
          <section className={styles.card} aria-labelledby="card-ocupacion">
            <h2 id="card-ocupacion" className={styles.cardTitle}>
              Ocupación
            </h2>
            <p className={styles.metric}>
              {ocupacion ? pct.format(ocupacion.ocupacionPct / 100) : '—'}
            </p>
            {ocupacion && (
              <dl className={styles.breakdown}>
                <div className={styles.breakdownRow}>
                  <dt>Slots reservados</dt>
                  <dd>{ocupacion.slotsReservados}</dd>
                </div>
                <div className={styles.breakdownRow}>
                  <dt>Slots disponibles</dt>
                  <dd>{ocupacion.slotsDisponibles}</dd>
                </div>
              </dl>
            )}
          </section>

          {/* Tarjeta de ingresos (RN-ADM-03) */}
          <section className={styles.card} aria-labelledby="card-ingresos">
            <h2 id="card-ingresos" className={styles.cardTitle}>
              Ingresos
            </h2>
            <p className={styles.metric}>
              {ingresos ? eur.format(ingresos.total) : '—'}
            </p>
            {ingresos && (
              <dl className={styles.breakdown}>
                <div className={styles.breakdownRow}>
                  <dt>Redsys</dt>
                  <dd>{eur.format(ingresos.porMetodo.REDSYS)}</dd>
                </div>
                <div className={styles.breakdownRow}>
                  <dt>Efectivo</dt>
                  <dd>{eur.format(ingresos.porMetodo.CASH)}</dd>
                </div>
              </dl>
            )}
          </section>
        </div>
      )}

      {/* Exportación CSV del rango (RN-ADM-04) */}
      <div className={styles.exportRow}>
        <button
          type="button"
          className={`p-btn p-btn-primary ${styles.exportBtn}`}
          onClick={handleExport}
          disabled={exporting || loading}
        >
          {exporting ? 'Generando…' : 'Exportar CSV'}
        </button>
        {exportError && (
          <p className="p-error" role="alert">
            {exportError}
          </p>
        )}
      </div>
    </div>
  );
}
