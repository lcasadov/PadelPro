// pagos-redsys-online (Grupo 6) — pagosApi.
// Service del journey de pago online del jugador. Usa el httpClient compartido
// (`api`) para heredar el interceptor de refresh de sesión, y reutiliza el mapeo
// de error tipado del backend (`toReservaApiError`) para no duplicar la traducción
// del error shape real `{ error, message, timestamp, details? }`.
//
// Seguridad (RN-PAY-03): los datos de tarjeta NUNCA pasan por PadelPro. Este
// service solo obtiene los parámetros firmados del TPV (para redirigir con un form)
// y consulta el estado del pago; la fuente de verdad del estado es el webhook
// (server-to-server), no la URL de retorno del navegador.
import { api } from './httpClient';
import { toReservaApiError, type PaymentStatus } from './reservasApi';

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` };
}

/**
 * Respuesta de `POST /api/pagos/iniciar`. Los tres campos `ds*` + `redsysUrl` son
 * para construir y auto-enviar (POST) el formulario firmado al TPV de Redsys. El
 * `amount` es informativo (importe congelado en backend, RN-RES-03); el importe
 * autoritativo en céntimos viaja codificado dentro de `dsMerchantParameters`.
 */
export interface IniciarPagoResponse {
  pagoId: string;
  redsysOrderId: string;
  redsysUrl: string;
  amount: number;
  status: PaymentStatus;
  dsSignatureVersion: string;
  dsMerchantParameters: string;
  dsSignature: string;
}

/** Pago del historial `GET /api/pagos`. `redsysOrderId`/`transactionId` son
 *  opcionales (referencia Redsys para mostrar en la confirmación); si el backend
 *  no los envía se hace fallback al `id` del pago. */
export interface PagoHistorial {
  id: string;
  reservaId: string;
  amount: number;
  method?: 'REDSYS' | 'CASH';
  status: PaymentStatus;
  paidAt?: string | null;
  redsysOrderId?: string | null;
  transactionId?: string | null;
}

/**
 * POST /api/pagos/iniciar — solo owner (RN-AUTH-04). Devuelve el form firmado del
 * TPV. Errores del contrato: 403 (no owner), 404 (reserva inexistente), 409 (pago
 * ya IN_PROGRESS/PAID), 422 (reserva cancelada). Se mapean a `ReservaApiError`.
 */
export async function iniciarPago(
  token: string,
  reservaId: string
): Promise<IniciarPagoResponse> {
  try {
    const { data } = await api.post<IniciarPagoResponse>(
      '/pagos/iniciar',
      { reservaId },
      { headers: authHeader(token) }
    );
    return data;
  } catch (err) {
    toReservaApiError(err);
  }
}

/**
 * GET /api/pagos — historial de pagos propios. `token` es opcional: al volver del
 * TPV (UrlOK/UrlKO) el access token en memoria puede haberse perdido (RN-AUTH-09,
 * recarga de página completa); en ese caso se omite el header y se deja que el
 * interceptor del httpClient restaure la sesión vía la cookie de refresh. Normaliza
 * un cuerpo paginado `{ data: [...] }` por robustez.
 */
export async function getPagos(token: string | null): Promise<PagoHistorial[]> {
  try {
    const { data } = await api.get<PagoHistorial[] | { data?: PagoHistorial[] }>('/pagos', {
      headers: token ? authHeader(token) : undefined,
    });
    if (Array.isArray(data)) return data;
    return Array.isArray(data?.data) ? data.data : [];
  } catch (err) {
    toReservaApiError(err);
  }
}
