// 6.2 — adminUsuariosApi — panel de administración de usuarios (D7).
// Reutiliza los endpoints admin existentes del backend:
//   GET    /api/admin/usuarios?status=&page=&size=   (listar/filtrar)
//   PATCH  /api/admin/usuarios/{id}/aprobar          (PENDING → ACTIVE)
//   PATCH  /api/admin/usuarios/{id}                  (cambiar estado)
//   DELETE /api/admin/usuarios/{id}                  (desactivar)
//   PATCH  /api/admin/usuarios/{id}/reset-password   (contraseña temporal, D3/D9)
//   POST   /api/admin/usuarios                       (alta directa ACTIVE — change usuarios-alta-edicion-email D2/D6)
//   PATCH  /api/admin/usuarios/{id}                  (editar datos de contacto, sin rol — D7)
import { api } from './httpClient';

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
  phone?: string;
}

// Alta directa (D2/D6): el admin teclea nombre, email, teléfono y rol.
// La contraseña NO se pide — la genera el backend (D2/D3) y la comunica por email.
export interface CrearUsuarioInput {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  role: string;
}

// Edición (D7): solo datos de contacto. El rol queda fuera para evitar
// escalada de privilegios; el estado se gestiona con las acciones dedicadas.
export interface EditarUsuarioInput {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
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

// Alta directa de usuario (status ACTIVE). El backend genera la contraseña
// temporal e ignora cualquier campo `password`, por eso no lo enviamos (D2/D3).
// `login` se deriva del email (login inmutable = identificador de la cuenta).
// Un email duplicado devuelve 409 (USUARIOS_EMAIL_CONFLICT).
export async function crearUsuarioApi(
  token: string,
  input: CrearUsuarioInput
): Promise<AdminUsuario> {
  const { data } = await api.post<AdminUsuario>(
    '/admin/usuarios',
    {
      login: input.email,
      firstName: input.firstName,
      lastName: input.lastName,
      email: input.email,
      phone: input.phone,
      role: input.role,
    },
    authHeader(token)
  );
  return data;
}

// Edición de datos de contacto (sin rol ni estado — D7). Un email mal formado
// devuelve 400 (VALIDATION_ERROR).
export async function editarUsuarioApi(
  token: string,
  id: number,
  input: EditarUsuarioInput
): Promise<AdminUsuario> {
  const { data } = await api.patch<AdminUsuario>(
    `/admin/usuarios/${id}`,
    {
      firstName: input.firstName,
      lastName: input.lastName,
      email: input.email,
      phone: input.phone,
    },
    authHeader(token)
  );
  return data;
}
