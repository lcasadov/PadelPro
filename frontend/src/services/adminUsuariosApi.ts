// 6.2 — adminUsuariosApi — panel de administración de usuarios (D7).
// Reutiliza los endpoints admin existentes del backend:
//   GET    /api/admin/usuarios?status=&page=&size=   (listar/filtrar)
//   PATCH  /api/admin/usuarios/{id}/aprobar          (PENDING → ACTIVE)
//   PATCH  /api/admin/usuarios/{id}                  (cambiar estado)
//   DELETE /api/admin/usuarios/{id}                  (desactivar)
//   PATCH  /api/admin/usuarios/{id}/reset-password   (contraseña temporal, D3/D9)
import axios from 'axios';

const api = axios.create({ baseURL: '/api', withCredentials: true });

function authHeader(token: string) {
  return { headers: { Authorization: `Bearer ${token}` } };
}

export interface AdminUsuario {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  status: string;
  role: string;
}

export interface PagedUsuarios {
  content: AdminUsuario[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ListUsuariosParams {
  status?: string;
  page?: number;
  size?: number;
}

export interface ResetPasswordResponse {
  user_id: number;
  temporary_password: string;
  must_change_password: boolean;
}

export async function listUsuariosApi(
  token: string,
  params: ListUsuariosParams = {}
): Promise<PagedUsuarios> {
  const { data } = await api.get<PagedUsuarios>('/admin/usuarios', {
    ...authHeader(token),
    params,
  });
  return data;
}

export async function aprobarUsuarioApi(token: string, id: number): Promise<AdminUsuario> {
  const { data } = await api.patch<AdminUsuario>(
    `/admin/usuarios/${id}/aprobar`,
    {},
    authHeader(token)
  );
  return data;
}

export async function updateUsuarioEstadoApi(
  token: string,
  id: number,
  status: string
): Promise<AdminUsuario> {
  const { data } = await api.patch<AdminUsuario>(
    `/admin/usuarios/${id}`,
    { status },
    authHeader(token)
  );
  return data;
}

export async function desactivarUsuarioApi(token: string, id: number): Promise<void> {
  await api.delete(`/admin/usuarios/${id}`, authHeader(token));
}

export async function resetPasswordApi(
  token: string,
  id: number
): Promise<ResetPasswordResponse> {
  const { data } = await api.patch<ResetPasswordResponse>(
    `/admin/usuarios/${id}/reset-password`,
    {},
    authHeader(token)
  );
  return data;
}
