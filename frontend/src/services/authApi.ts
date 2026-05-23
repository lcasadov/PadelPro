// Stub — implementación real en Oleada 3
// Los tipos coinciden con docs/openapi.yaml (POST /api/v1/auth/login y /register)

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

// Stubs — lanzan error hasta Oleada 3
export async function loginApi(_req: LoginRequest): Promise<LoginResponse> {
  throw new Error('Not implemented yet');
}

export async function registerApi(_req: RegisterRequest): Promise<RegisterResponse> {
  throw new Error('Not implemented yet');
}
