// Oleada 3 — implementación real con axios
// Tipos coinciden con docs/openapi.yaml (POST /api/auth/login y /api/auth/register)
//
// auth-session-refresh: usa la instancia base compartida (interceptor de refresh).
// login/register llevan `_skipAuthRefresh` porque su 401 significa "credenciales
// inválidas" (aún no hay sesión), NO "access token caducado": no debe dispararse
// el flujo de renovación.
import { api } from './httpClient';

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  // acceso-cuenta-prod: el backend incluye el rol y el flag de cambio forzado (D9).
  role?: string;
  must_change_password?: boolean;
}

export interface RegisterResponse {
  id: number;
  email: string;
  role: string;
}

export interface ApiError {
  error: string;
  message?: string;
  details?: string[];
}

export async function loginApi(req: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/login', req, {
    _skipAuthRefresh: true,
  });
  return data;
}

export async function registerApi(req: RegisterRequest): Promise<RegisterResponse> {
  const { data } = await api.post<RegisterResponse>(
    '/auth/register',
    {
      first_name: req.firstName,
      last_name: req.lastName,
      email: req.email,
      password: req.password,
    },
    { _skipAuthRefresh: true }
  );
  return data;
}
