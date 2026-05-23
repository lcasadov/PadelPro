// Oleada 3 — implementación real con axios
// Tipos coinciden con docs/openapi.yaml (POST /api/auth/login y /api/auth/register)
import axios from 'axios';

const api = axios.create({ baseURL: '/api', withCredentials: true });

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
  const { data } = await api.post<LoginResponse>('/auth/login', req);
  return data;
}

export async function registerApi(req: RegisterRequest): Promise<RegisterResponse> {
  const { data } = await api.post<RegisterResponse>('/auth/register', {
    first_name: req.firstName,
    last_name: req.lastName,
    email: req.email,
    password: req.password,
  });
  return data;
}
