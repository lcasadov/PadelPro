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

/**
 * Participante adicional (D4). XOR estricto acorde a la validación del backend:
 *  - socio registrado → `{ userId }` (sin datos de externo)
 *  - invitado externo → `{ externalName }` (+ `externalPhone` opcional)
 * El payload envía exactamente una de las dos formas por participante.
 */
export type ParticipanteAdicional =
  | { userId: number }
  | { externalName: string; externalPhone?: string };

/** Payload de creación. NUNCA incluye importe (RN-RES-03): el precio lo
 *  calcula y congela el backend. */
export interface CrearReservaPayload {
  reservationDate: string;
  startTime: string;
  durationMinutes: number;
  participantesAdicionales?: ParticipanteAdicional[];
  notes?: string;
}

// ─── Partidas (partidas-unirse) ───────────────────────────────────────────────

/** Participante de una partida abierta en la proyección del listado. Solo nombre
 *  de display (sin PII: ni email ni teléfono, RN-RGPD-03). */
export interface PartidaParticipante {
  nombre: string;
  slotPosition: number;
  owner: boolean;
}

/** Partida abierta (reserva activa con plazas libres) del contrato
 *  `GET /api/partidas?fecha=YYYY-MM-DD`. Identificable por `reservaId`; `priceTotal`
 *  es informativo (el reparto "tu parte" lo calcula la UI, sin cobro online). */
export interface PartidaAbierta {
  reservaId: string;
  reservationDate: string;
  startTime: string;
  durationMinutes: number;
  plazasLibres: number;
  priceTotal: number;
  participantes: PartidaParticipante[];
}

/** Respuesta de `POST /api/reservas/{id}/unirse`. */
export interface UnirseResponse {
  participanteId: string | number;
  reservaId: string;
  userId: number;
  nombre: string;
  statusPago: string;
}

// ─── Errores tipados (D6) ─────────────────────────────────────────────────────

export type ReservaErrorCode =
  | 'AUTH_REQUIRED'
  | 'CONFLICT'
  | 'VALIDATION_ERROR'
  | 'PARTICIPANTS_LIMIT_EXCEEDED'
  | 'INVALID_STATE_TRANSITION'
  | 'CANCELLATION_DEADLINE_PASSED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'SERVER_ERROR'
  | 'NETWORK_ERROR'
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
  /** Lista de detalles textuales del backend (contrato real: `details`). */
  readonly details: string[];

  constructor(
    code: ReservaErrorCode,
    message: string,
    status: number,
    fieldErrors: FieldError[] = [],
    details: string[] = []
  ) {
    super(message);
    this.name = 'ReservaApiError';
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.details = details;
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
  /** Contrato real: lista de detalles del error (p. ej. campos inválidos). */
  details?: string[];
  /** Compat: algunos endpoints devolvían `errors[]` tipado por campo. */
  errors?: FieldError[];
}

/** Códigos de negocio que llegan en el cuerpo (`error`) del backend. Los códigos
 *  derivados del status (AUTH_REQUIRED, SERVER_ERROR, NETWORK_ERROR) NO se listan
 *  aquí: se infieren de `codeFromStatus`. */
const KNOWN_CODES: ReservaErrorCode[] = [
  'AUTH_REQUIRED',
  'CONFLICT',
  'VALIDATION_ERROR',
  'PARTICIPANTS_LIMIT_EXCEEDED',
  'INVALID_STATE_TRANSITION',
  'CANCELLATION_DEADLINE_PASSED',
  'FORBIDDEN',
  'NOT_FOUND',
];

/** Traduce el status HTTP a un `code` cuando el cuerpo no trae uno reconocible.
 *  Diferencia el 401 (sesión), el 5xx (servidor) y el error de red (status 0). */
function codeFromStatus(status: number): ReservaErrorCode {
  switch (status) {
    case 400:
      return 'VALIDATION_ERROR';
    case 401:
      return 'AUTH_REQUIRED';
    case 403:
      return 'FORBIDDEN';
    case 404:
      return 'NOT_FOUND';
    case 409:
      return 'CONFLICT';
    default:
      if (status === 0) return 'NETWORK_ERROR';
      if (status >= 500) return 'SERVER_ERROR';
      return 'UNKNOWN';
  }
}

/** Convierte cualquier fallo axios en un ReservaApiError tipado. El backend en vivo
 *  responde `{ error, message, timestamp, details? }` (la clave del código es `error`,
 *  no `code`; la lista es `details`, no `fieldErrors`) y usa HTTP 422 para los tres
 *  errores de negocio (PARTICIPANTS_LIMIT_EXCEEDED, INVALID_STATE_TRANSITION,
 *  CANCELLATION_DEADLINE_PASSED), que comparten status. Por eso el código se toma
 *  SIEMPRE del valor textual del cuerpo cuando es un `ReservaErrorCode` conocido; solo
 *  se cae a `codeFromStatus` si el cuerpo no lo trae (401 sesión, 5xx servidor, red).
 *  Re-lanza. Exportado para reutilizarlo desde otras services del mismo dominio
 *  (p. ej. `pagosApi`) sin duplicar el mapeo del error shape real del backend. */
export function toReservaApiError(error: unknown): never {
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
  const details = Array.isArray(body?.details)
    ? body!.details!.filter((d): d is string => typeof d === 'string')
    : [];

  throw new ReservaApiError(code, message, status, fieldErrors, details);
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

// ─── Partidas: listar, unirse, abandonar (partidas-unirse) ─────────────────────

/** GET /api/partidas?fecha=YYYY-MM-DD — partidas abiertas de la fecha (reservas
 *  activas con plazas libres). El backend devuelve un ARRAY JSON plano; se normaliza
 *  por robustez si llegara envuelto en `{ data: [...] }`. */
export async function getPartidasAbiertas(token: string, fecha: string): Promise<PartidaAbierta[]> {
  try {
    const { data } = await api.get<PartidaAbierta[] | { data?: PartidaAbierta[] }>('/partidas', {
      headers: authHeader(token),
      params: { fecha },
    });
    if (Array.isArray(data)) return data;
    return Array.isArray(data?.data) ? data.data : [];
  } catch (err) {
    toReservaApiError(err);
  }
}

/** POST /api/reservas/{id}/unirse (sin body) — une al usuario como participante
 *  no-owner. Errores del contrato: 404 (inexistente), 409 (ya participante, mapeado
 *  a CONFLICT por status), 422 (completa o estado no unible). Las páginas
 *  discriminan por `err.status` para no depender del código textual del cuerpo. */
export async function unirseReserva(token: string, reservaId: string): Promise<UnirseResponse> {
  try {
    const { data } = await api.post<UnirseResponse>(
      `/reservas/${reservaId}/unirse`,
      undefined,
      { headers: authHeader(token) }
    );
    return data;
  } catch (err) {
    toReservaApiError(err);
  }
}

/** DELETE /api/reservas/{id}/participacion — 204. Abandona una partida en la que
 *  el usuario participa como no-owner (libera su plaza). El owner no puede abandonar
 *  (debe cancelar): el backend lo rechaza. */
export async function abandonarReserva(token: string, reservaId: string): Promise<void> {
  try {
    await api.delete(`/reservas/${reservaId}/participacion`, { headers: authHeader(token) });
  } catch (err) {
    toReservaApiError(err);
  }
}
