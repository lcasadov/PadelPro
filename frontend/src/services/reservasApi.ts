// reservas-ui-jugador (Grupo 2, D6) — reservasApi.
// Service axios único para el journey del jugador: disponibilidad, crear,
// listar, detalle y cancelar. Sigue el patrón existente (authApi/usuariosApi):
//   axios.create({ baseURL: '/api', withCredentials: true }) y `token` explícito
//   por llamada vía header Authorization: Bearer. No hay interceptor global.
//
// Centraliza (D6) el mapeo del error shape real del backend (verificado en vivo)
//   { error: "<CODE>", message, timestamp }
// donde la clave del código es `error` (no `code`) y los errores de negocio
// (PARTICIPANTS_LIMIT_EXCEEDED, INVALID_STATE_TRANSITION, CANCELLATION_DEADLINE_PASSED)
// llegan con HTTP 422. Se traduce a un error tipado `ReservaApiError` con `.code`
// para que las páginas rendericen mensajes específicos sin duplicar strings.
//
// Tipos derivados de docs/openapi.yaml (Reservas). El flag `creable` de
// `Tramo` es el delta aditivo del backend (D7): true sii el tramo no tiene
// ocupación y admite crear reserva. Se trata como false si el backend no lo
// envía (fail-safe): nunca se ofrece crear en un tramo no marcado.
import { type AxiosError } from 'axios';
import { api } from './httpClient';

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` };
}

// ─── Tipos de dominio (docs/openapi.yaml) ────────────────────────────────────

export type ReservationStatus =
  | 'PENDING_CONFIRMATION'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'COMPLETED';

export type ReservationChannel = 'WEB' | 'TELEGRAM';

export type PaymentStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'PAID'
  | 'FAILED'
  | 'CANCELLED'
  | 'REFUNDED';

/** Tramo horario disponible. `creable` (delta backend, D7) marca los que
 *  admiten crear reserva; los parciales (solo unibles) llegan con `creable:false`. */
export interface Tramo {
  horaInicio: string;
  duracionMinutos: number;
  plazasLibres: number;
  creable: boolean;
}

export interface DisponibilidadResponse {
  fecha: string;
  tramosDisponibles: Tramo[];
}

export interface PagoResponse {
  id: string;
  reservaId: string;
  amount: number;
  method?: 'REDSYS' | 'CASH';
  status: PaymentStatus;
  redsysOrderId?: string | null;
  paymentUrl?: string | null;
  transactionId?: string | null;
  paidAt?: string | null;
  createdAt: string;
  updatedAt?: string;
}

/** Participante real (contrato backend en vivo): usa `owner` (no `isOwner`) y
 *  `externalName` (no `nombre`); NO trae `id` ni `joinedAt`. */
export interface ParticipanteResponse {
  userId?: number | null;
  externalName?: string | null;
  externalPhone?: string | null;
  slotPosition?: number;
  owner: boolean;
}

export interface ReservaResponse {
  id: string;
  reservationDate: string;
  startTime: string;
  endTime?: string;
  durationMinutes: number;
  status: ReservationStatus;
  channel: ReservationChannel;
  ownerId?: number;
  priceTotal: number;
  notes?: string | null;
  participants: ParticipanteResponse[];
  pago?: PagoResponse | null;
  telegramMessageId?: string | null;
  createdAt: string;
  updatedAt?: string;
}

/** Participante adicional (v1: solo `externalName`, D4). */
export interface ParticipanteAdicional {
  externalName: string;
  externalPhone?: string;
}

/** Payload de creación. NUNCA incluye importe (RN-RES-03): el precio lo
 *  calcula y congela el backend. */
export interface CrearReservaPayload {
  reservationDate: string;
  startTime: string;
  durationMinutes: number;
  participantesAdicionales?: ParticipanteAdicional[];
  notes?: string;
}

// ─── Errores tipados (D6) ─────────────────────────────────────────────────────

export type ReservaErrorCode =
  | 'CONFLICT'
  | 'VALIDATION_ERROR'
  | 'PARTICIPANTS_LIMIT_EXCEEDED'
  | 'INVALID_STATE_TRANSITION'
  | 'CANCELLATION_DEADLINE_PASSED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'UNKNOWN';

export interface FieldError {
  field: string;
  message: string;
}

/** Error de negocio de la API de reservas, tipado por `code`. */
export class ReservaApiError extends Error {
  readonly code: ReservaErrorCode;
  readonly status: number;
  readonly fieldErrors: FieldError[];

  constructor(code: ReservaErrorCode, message: string, status: number, fieldErrors: FieldError[] = []) {
    super(message);
    this.name = 'ReservaApiError';
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
    Object.setPrototypeOf(this, ReservaApiError.prototype);
  }
}

export function isReservaApiError(err: unknown): err is ReservaApiError {
  return err instanceof ReservaApiError;
}

interface BackendErrorBody {
  /** El backend en vivo usa la clave `error` para el código; `code` se acepta por
   *  compatibilidad. */
  error?: string;
  code?: string;
  message?: string;
  errors?: FieldError[];
}

const KNOWN_CODES: ReservaErrorCode[] = [
  'CONFLICT',
  'VALIDATION_ERROR',
  'PARTICIPANTS_LIMIT_EXCEEDED',
  'INVALID_STATE_TRANSITION',
  'CANCELLATION_DEADLINE_PASSED',
  'FORBIDDEN',
  'NOT_FOUND',
];

/** Traduce el status HTTP a un `code` cuando el cuerpo no trae uno reconocible. */
function codeFromStatus(status: number): ReservaErrorCode {
  switch (status) {
    case 400:
      return 'VALIDATION_ERROR';
    case 403:
      return 'FORBIDDEN';
    case 404:
      return 'NOT_FOUND';
    case 409:
      return 'CONFLICT';
    default:
      return 'UNKNOWN';
  }
}

/** Convierte cualquier fallo axios en un ReservaApiError tipado. El backend en vivo
 *  responde `{ error, message, timestamp }` (la clave del código es `error`, no `code`)
 *  y usa HTTP 422 para los tres errores de negocio (PARTICIPANTS_LIMIT_EXCEEDED,
 *  INVALID_STATE_TRANSITION, CANCELLATION_DEADLINE_PASSED), que comparten status.
 *  Por eso el código se toma SIEMPRE del valor textual del cuerpo cuando es un
 *  `ReservaErrorCode` conocido; solo se cae a `codeFromStatus` si el cuerpo no lo
 *  trae. Re-lanza. */
function toReservaApiError(error: unknown): never {
  const axErr = error as AxiosError<BackendErrorBody>;
  const status = axErr.response?.status ?? 0;
  const body = axErr.response?.data;

  const bodyCode = body?.error ?? body?.code;
  const code: ReservaErrorCode =
    bodyCode && (KNOWN_CODES as string[]).includes(bodyCode)
      ? (bodyCode as ReservaErrorCode)
      : codeFromStatus(status);

  const message = body?.message ?? axErr.message ?? 'Error inesperado';
  const fieldErrors = Array.isArray(body?.errors) ? body!.errors! : [];

  throw new ReservaApiError(code, message, status, fieldErrors);
}

// ─── Endpoints ────────────────────────────────────────────────────────────────

/** GET /api/reservas/disponibles?fecha=YYYY-MM-DD */
export async function getDisponibilidad(token: string, fecha: string): Promise<DisponibilidadResponse> {
  try {
    const { data } = await api.get<DisponibilidadResponse>('/reservas/disponibles', {
      headers: authHeader(token),
      params: { fecha },
    });
    return data;
  } catch (err) {
    toReservaApiError(err);
  }
}

/** POST /api/reservas — con header Idempotency-Key (RN-RES-05). Sin importe (RN-RES-03). */
export async function crearReserva(
  token: string,
  payload: CrearReservaPayload,
  idempotencyKey: string
): Promise<ReservaResponse> {
  try {
    const { data } = await api.post<ReservaResponse>('/reservas', payload, {
      headers: {
        ...authHeader(token),
        'Idempotency-Key': idempotencyKey,
      },
    });
    return data;
  } catch (err) {
    toReservaApiError(err);
  }
}

/** GET /api/reservas — reservas del usuario autenticado (owner o participante).
 *  El backend en vivo devuelve un ARRAY JSON plano `[ {reserva}, ... ]`. Se normaliza
 *  de forma robusta por si llegara envuelto en un objeto paginado `{ data: [...] }`. */
export async function getMisReservas(token: string): Promise<ReservaResponse[]> {
  try {
    const { data } = await api.get<ReservaResponse[] | { data?: ReservaResponse[] }>('/reservas', {
      headers: authHeader(token),
    });
    if (Array.isArray(data)) return data;
    return Array.isArray(data?.data) ? data.data : [];
  } catch (err) {
    toReservaApiError(err);
  }
}

/** GET /api/reservas/{id} — 403 (ajena) / 404 (inexistente) se mapean por code. */
export async function getReserva(token: string, id: string): Promise<ReservaResponse> {
  try {
    const { data } = await api.get<ReservaResponse>(`/reservas/${id}`, {
      headers: authHeader(token),
    });
    return data;
  } catch (err) {
    toReservaApiError(err);
  }
}

/** DELETE /api/reservas/{id} — 204. 422 CANCELLATION_DEADLINE_PASSED / 403 mapeados. */
export async function cancelarReserva(token: string, id: string): Promise<void> {
  try {
    await api.delete(`/reservas/${id}`, { headers: authHeader(token) });
  } catch (err) {
    toReservaApiError(err);
  }
}
