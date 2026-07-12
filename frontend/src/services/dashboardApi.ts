// 5.2 — dashboardApi — cliente del dashboard de administración del club
// (capability administracion-club, D5). Consume los endpoints dedicados del
// backend bajo /api/admin/dashboard/** (todos exigen ROLE_ADMIN — RN-ADM-01).
//   GET /api/admin/dashboard/ocupacion?fechaInicio&fechaFin
//   GET /api/admin/dashboard/ingresos?fechaInicio&fechaFin
//   GET /api/admin/dashboard/exportar?fechaInicio&fechaFin  (text/csv, descarga)
// Reutiliza la instancia axios compartida `api` (baseURL /api, mismo origen,
// interceptor de refresh de sesión).
import { api } from './httpClient';

function authHeader(token: string) {
  return { headers: { Authorization: `Bearer ${token}` } };
}

/** Rango de fechas del informe (YYYY-MM-DD, inclusivo). */
export interface RangoFechas {
  fechaInicio: string;
  fechaFin: string;
}

/** Métricas de ocupación (RN-ADM-02). */
export interface OcupacionResponse {
  fechaInicio: string;
  fechaFin: string;
  slotsReservados: number;
  slotsDisponibles: number;
  ocupacionPct: number;
}

/** Métricas de ingresos con desglose por método (RN-ADM-03). */
export interface IngresosResponse {
  fechaInicio: string;
  fechaFin: string;
  total: number;
  porMetodo: {
    REDSYS: number;
    CASH: number;
  };
}

export async function getOcupacion(
  token: string,
  rango: RangoFechas
): Promise<OcupacionResponse> {
  const { data } = await api.get<OcupacionResponse>('/admin/dashboard/ocupacion', {
    ...authHeader(token),
    params: rango,
  });
  return data;
}

export async function getIngresos(
  token: string,
  rango: RangoFechas
): Promise<IngresosResponse> {
  const { data } = await api.get<IngresosResponse>('/admin/dashboard/ingresos', {
    ...authHeader(token),
    params: rango,
  });
  return data;
}

/**
 * Descarga el informe de uso en CSV del rango seleccionado (RN-ADM-04).
 * Pide el recurso como `blob` (el backend responde text/csv con
 * Content-Disposition attachment) y dispara la descarga en el navegador
 * creando un object URL temporal y un <a download> sintético.
 */
export async function exportarCsv(token: string, rango: RangoFechas): Promise<void> {
  const response = await api.get('/admin/dashboard/exportar', {
    ...authHeader(token),
    params: rango,
    responseType: 'blob',
  });

  const blob =
    response.data instanceof Blob
      ? response.data
      : new Blob([response.data as BlobPart], { type: 'text/csv' });

  const filename = filenameFromDisposition(
    response.headers?.['content-disposition'],
    rango
  );

  triggerBlobDownload(blob, filename);
}

/** Extrae el filename del header Content-Disposition; si no viene, deriva uno del rango. */
function filenameFromDisposition(
  disposition: string | undefined,
  rango: RangoFechas
): string {
  if (disposition) {
    const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
    if (match?.[1]) {
      return decodeURIComponent(match[1].trim());
    }
  }
  return `informe-uso-${rango.fechaInicio}_${rango.fechaFin}.csv`;
}

/** Crea un object URL para el blob, dispara la descarga y libera el URL. */
function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}
