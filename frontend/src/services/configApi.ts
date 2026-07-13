// admin-config-club — configApi.
// Servicio de la configuración global del club (capability configuracion-club).
// Endpoints (solo ADMIN, ROLE_ADMIN en backend):
//   GET   /api/admin/sistema/config
//   PATCH /api/admin/sistema/config   (actualización parcial)
//
// Usa el httpClient compartido (`api`) para heredar el interceptor de refresh y el
// mapeo de error tipado del backend (`toReservaApiError`).
//
// Contrato real (verificado contra el backend):
//  - El GET NO devuelve los secretos: solo `telegramBotConfigured` / `redsysConfigured`
//    (booleanos). Los secretos se escriben, nunca se leen.
//  - El PATCH exige SIEMPRE `clubName`, `pistaState`, `paymentGateway` y
//    `maxParticipantsPerPista` (se setean incondicionalmente en el backend); el form
//    los reenvía siempre precargados del GET.
//  - `pricePerHour`, `cancellationDeadlineHours` y los secretos son opcionales: si se
//    omiten (null), el backend conserva el valor almacenado.
//  - Si `paymentGateway=REDSYS`, el backend exige `redsysMerchantId`+`redsysMerchantKey`
//    en la request (por eso el form los pide al activar REDSYS).
import { api } from './httpClient';
import { toReservaApiError } from './reservasApi';

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export type PistaState = 'ACTIVA' | 'MANTENIMIENTO';
export type PaymentGateway = 'CASH' | 'REDSYS';

/** Respuesta de `GET /api/admin/sistema/config` (`SystemConfigResponse`). */
export interface SystemConfig {
  clubName: string;
  clubDescription: string | null;
  pistaState: PistaState;
  paymentGateway: PaymentGateway;
  maxParticipantsPerPista: number;
  pricePerHour: number;
  cancellationDeadlineHours: number;
  /** true si hay bot token de Telegram configurado (el valor nunca se devuelve). */
  telegramBotConfigured: boolean;
  /** true si hay clave Redsys configurada (el valor nunca se devuelve). */
  redsysConfigured: boolean;
  updatedAt: string;
}

/** Cuerpo de `PATCH /api/admin/sistema/config` (`UpdateSystemConfigRequest`).
 *  Los campos secretos y de precio son opcionales: omitir = conservar. */
export interface UpdateSystemConfig {
  clubName: string;
  clubDescription: string | null;
  pistaState: PistaState;
  paymentGateway: PaymentGateway;
  maxParticipantsPerPista: number;
  pricePerHour?: number;
  cancellationDeadlineHours?: number;
  telegramBotToken?: string;
  telegramWebhookSecret?: string;
  telegramGroupId?: string;
  redsysMerchantId?: string;
  redsysMerchantKey?: string;
}

/** GET /api/admin/sistema/config — configuración actual del club (solo ADMIN). */
export async function getSystemConfig(token: string): Promise<SystemConfig> {
  try {
    const { data } = await api.get<SystemConfig>('/admin/sistema/config', {
      headers: authHeader(token),
    });
    return data;
  } catch (err) {
    toReservaApiError(err);
  }
}

/** PATCH /api/admin/sistema/config — actualización parcial (solo ADMIN). Errores del
 *  contrato mapeados a `ReservaApiError` (400 VALIDATION_ERROR, 403 FORBIDDEN). */
export async function updateSystemConfig(
  token: string,
  body: UpdateSystemConfig
): Promise<SystemConfig> {
  try {
    const { data } = await api.patch<SystemConfig>('/admin/sistema/config', body, {
      headers: authHeader(token),
    });
    return data;
  } catch (err) {
    toReservaApiError(err);
  }
}
