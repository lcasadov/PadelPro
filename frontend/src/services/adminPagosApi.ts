// pagos-simulador-gestion (D3) — adminPagosApi.
// Gestión admin de cobros. Reutiliza los endpoints existentes de la capability
// pagos-redsys:
//   GET  /api/admin/pagos                       (listar todos los pagos/reservas)
//   POST /api/admin/pagos/{reservaId}/efectivo  (marcar PAID por cobro presencial)
//
// Usa el httpClient compartido (`api`) para heredar el interceptor de refresh y el
// mapeo de error tipado del backend (`toReservaApiError`). El backend en vivo puede
// devolver la lista como array plano o envuelta en `{ data: [...] }`: se normaliza.
import { api } from './httpClient';
import { toReservaApiError, type PaymentStatus } from './reservasApi';

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` };
}

/**
 * Pago del historial admin (`GET /api/admin/pagos`). Trae el estado, la reserva,
 * el importe (en euros), el titular y la fecha del cobro. `titular`/`paidAt`/
 * `createdAt` son opcionales por robustez ante variaciones del DTO backend; la UI
 * hace fallback ('—' / createdAt) cuando falten. La fecha a mostrar es `paidAt`
 * (si pagado) o `createdAt`.
 */
export interface AdminPago {
  id: string;
  reservaId: string;
  amount: number;
  status: PaymentStatus;
  method?: string | null;
  titular?: string | null;
  paidAt?: string | null;
  createdAt?: string | null;
}

/** Forma cruda del backend (`PagoHistorialResponse`): id=`pagoId`, titular=`ownerName`. */
interface PagoHistorialRaw {
  pagoId?: string;
  id?: string;
  reservaId: string;
  ownerName?: string | null;
  titular?: string | null;
  amount: number;
  status: PaymentStatus;
  method?: string | null;
  reservationDate?: string | null;
  paidAt?: string | null;
  createdAt?: string | null;
}

/** GET /api/admin/pagos — todos los pagos/reservas del club (solo ADMIN, ROLE_ADMIN
 *  en backend). Normaliza array plano o `{ data: [...] }` y mapea los nombres reales
 *  del backend (`pagoId`→id, `ownerName`→titular). */
export async function getAdminPagos(token: string): Promise<AdminPago[]> {
  try {
    const { data } = await api.get<PagoHistorialRaw[] | { data?: PagoHistorialRaw[] }>(
      '/admin/pagos',
      { headers: authHeader(token) }
    );
    const list = Array.isArray(data) ? data : Array.isArray(data?.data) ? data.data : [];
    return list.map((p) => ({
      id: p.pagoId ?? p.id ?? p.reservaId,
      reservaId: p.reservaId,
      amount: p.amount,
      status: p.status,
      method: p.method ?? null,
      titular: p.ownerName ?? p.titular ?? null,
      paidAt: p.paidAt ?? null,
      createdAt: p.createdAt ?? p.reservationDate ?? null,
    }));
  } catch (err) {
    toReservaApiError(err);
  }
}

/** POST /api/admin/pagos/{reservaId}/efectivo — marca el pago PAID (cobro presencial).
 *  Solo ADMIN. Errores del contrato mapeados a `ReservaApiError` (404 reserva/pago
 *  inexistente, 409/422 ya pagado). */
export async function marcarPagoEfectivo(token: string, reservaId: string): Promise<void> {
  try {
    await api.post(`/admin/pagos/${reservaId}/efectivo`, undefined, {
      headers: authHeader(token),
    });
  } catch (err) {
    toReservaApiError(err);
  }
}
