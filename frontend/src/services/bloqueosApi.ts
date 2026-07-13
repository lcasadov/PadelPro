// bloqueos-pista-eventos — bloqueosApi.
// Gestión admin de bloqueos de franjas horarias de la pista (torneos/eventos).
// Endpoints (solo ADMIN, ROLE_ADMIN en backend):
//   GET    /api/admin/bloqueos?fecha=YYYY-MM-DD   listar bloqueos de la fecha
//   POST   /api/admin/bloqueos                    crear bloqueo(s) { fecha, horas[], motivo }
//   DELETE /api/admin/bloqueos/{id}               eliminar un bloqueo
//
// Usa el httpClient compartido (`api`) para heredar el interceptor de refresh y el
// mapeo de error tipado del backend (`toReservaApiError`). Al crear, si alguna franja
// solapa una reserva activa el backend responde 409 (CONFLICT) con `details` listando
// las franjas en conflicto — se propaga como ReservaApiError para que la UI lo muestre.
import { api } from './httpClient';
import { toReservaApiError } from './reservasApi';

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` };
}

/** Un bloqueo de franja (`GET`/`POST /api/admin/bloqueos`). `hora` = "HH:mm". */
export interface Bloqueo {
  id: number;
  fecha: string;
  hora: string;
  motivo: string | null;
}

/** GET /api/admin/bloqueos?fecha= — bloqueos de la fecha (solo ADMIN). Normaliza
 *  array plano o `{ data: [...] }` por robustez ante variaciones del backend. */
export async function getBloqueos(token: string, fecha: string): Promise<Bloqueo[]> {
  try {
    const { data } = await api.get<Bloqueo[] | { data?: Bloqueo[] }>('/admin/bloqueos', {
      headers: authHeader(token),
      params: { fecha },
    });
    return Array.isArray(data) ? data : Array.isArray(data?.data) ? data.data : [];
  } catch (err) {
    toReservaApiError(err);
  }
}

/** POST /api/admin/bloqueos — bloquea una o varias franjas de una fecha con un motivo.
 *  Todo-o-nada: si alguna franja solapa una reserva activa, el backend responde 409 y
 *  no crea ninguna (se propaga como ReservaApiError con code CONFLICT + details). */
export async function crearBloqueos(
  token: string,
  input: { fecha: string; horas: string[]; motivo: string }
): Promise<Bloqueo[]> {
  try {
    const { data } = await api.post<Bloqueo[] | { data?: Bloqueo[] }>('/admin/bloqueos', input, {
      headers: authHeader(token),
    });
    return Array.isArray(data) ? data : Array.isArray(data?.data) ? data.data : [];
  } catch (err) {
    toReservaApiError(err);
  }
}

/** DELETE /api/admin/bloqueos/{id} — desbloquea una franja (solo ADMIN). */
export async function eliminarBloqueo(token: string, id: number): Promise<void> {
  try {
    await api.delete(`/admin/bloqueos/${id}`, { headers: authHeader(token) });
  } catch (err) {
    toReservaApiError(err);
  }
}
