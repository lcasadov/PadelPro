// T-144 — usuariosApi — GET/PATCH /api/usuarios/me
import { api } from './httpClient';

export interface UsuarioMe {
  id: number;
  login: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  status: string;
  role: string;
  telegramLinked: boolean;
}

export interface UpdateMeRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string | null;
}

export async function getMeApi(token: string): Promise<UsuarioMe> {
  const { data } = await api.get<UsuarioMe>('/usuarios/me', {
    headers: { Authorization: `Bearer ${token}` },
  });
  return data;
}

export async function updateMeApi(token: string, req: UpdateMeRequest): Promise<UsuarioMe> {
  const { data } = await api.patch<UsuarioMe>('/usuarios/me', req, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return data;
}

// acceso-cuenta-prod (D9) — cambio de contraseña propio.
// POST /api/usuarios/me/password → 204; 401 (current mal); 400 INVALID_PASSWORD.
export interface ChangePasswordRequest {
  current_password: string;
  new_password: string;
}

export async function changeMyPasswordApi(
  token: string,
  req: ChangePasswordRequest
): Promise<void> {
  // `_skipAuthRefresh`: un 401 aquí significa "contraseña actual incorrecta"
  // (auth-session-refresh), no sesión caducada — no debe disparar el refresh.
  await api.post('/usuarios/me/password', req, {
    headers: { Authorization: `Bearer ${token}` },
    _skipAuthRefresh: true,
  });
}

// reservas-ui-jugador-fixes (D5) — búsqueda de socios para el buscador de
// participante registrado. GET /api/usuarios/buscar?q=<term> → [{ id, nombre }].
// Resultados mínimos (id + nombre) y acotados; el backend exige término mínimo y
// autenticación. Un término corto o sin coincidencias devuelve un array vacío.
export interface UsuarioBusqueda {
  id: number;
  nombre: string;
}

export async function buscarUsuariosApi(
  token: string,
  q: string
): Promise<UsuarioBusqueda[]> {
  const { data } = await api.get<UsuarioBusqueda[]>('/usuarios/buscar', {
    headers: { Authorization: `Bearer ${token}` },
    params: { q },
  });
  return Array.isArray(data) ? data : [];
}
