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

// ─── Simulador de pago (pagos-simulador-gestion, D1/D2) ───────────────────────

/** Payload de `POST /api/pagos/simular`. Datos de tarjeta SOLO en tránsito: se
 *  envían para decidir el resultado y NUNCA se persisten (RN-RGPD-04). `expiry`
 *  viaja como "MM/AA". */
export interface SimularPagoInput {
  reservaId: string;
  cardNumber: string;
  expiry: string;
  cvc: string;
}

/** Respuesta 200 de `POST /api/pagos/simular`. `resultado` decide el flujo de la
 *  UI: APPROVED → pantalla OK (el backend ya marcó el pago PAID); DECLINED →
 *  pantalla KO con reintento. `motivo` acompaña opcionalmente al rechazo. */
export interface SimularPagoResponse {
  resultado: 'APPROVED' | 'DECLINED';
  motivo?: string;
}

/**
 * POST /api/pagos/simular — checkout simulado (provisional hasta integrar Redsys).
 * Solo el dueño de la reserva con pago PENDING. Errores del contrato mapeados a
 * `ReservaApiError`: 400 (formato de tarjeta inválido → VALIDATION_ERROR), 403
 * (no-dueño → FORBIDDEN), 409/422 (ya pagado → CONFLICT/estado). El APPROVED y el
 * DECLINED llegan ambos como 200 en el cuerpo (no son errores HTTP).
 */
export async function simularPago(
  token: string,
  input: SimularPagoInput
): Promise<SimularPagoResponse> {
  try {
    const { data } = await api.post<SimularPagoResponse>('/pagos/simular', input, {
      headers: authHeader(token),
    });
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
