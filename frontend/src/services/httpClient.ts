// auth-session-refresh (Grupo 2, D3/D4) — cliente axios base compartido con
// interceptor de renovación silenciosa de sesión.
//
// Todas las services (reservasApi, usuariosApi, authApi, adminUsuariosApi) usan
// ESTA instancia para heredar el interceptor de respuesta: ante un 401 en una
// petición autenticada, se dispara UN ÚNICO POST /api/auth/refresh (single-flight),
// y al resolverse se reintenta la petición original con el nuevo access token.
// Si el refresh falla, se limpia la sesión y se redirige a login.
//
// El módulo NO conoce React ni react-router: el AuthProvider/routing registran
// dos callbacks vía `registerSessionHandlers` (onTokenRefreshed / onSessionExpired).
// El access token sigue viviendo SOLO en memoria de React (RN-AUTH-09): aquí no se
// persiste ni se cachea el token, solo se propaga el nuevo valor por callback.
import axios, {
  type AxiosError,
  type InternalAxiosRequestConfig,
} from 'axios';

// Propiedades de control que el interceptor añade/lee en la config de cada petición.
declare module 'axios' {
  export interface AxiosRequestConfig {
    /** Marca la petición ya reintestada tras un refresh: evita bucles. */
    _retry?: boolean;
    /**
     * Excluye la petición del flujo de refresh. Se usa en endpoints donde un 401
     * tiene semántica de credenciales, no de sesión caducada (login, register,
     * cambio de contraseña, y el propio /auth/refresh).
     */
    _skipAuthRefresh?: boolean;
  }
}

/** Instancia axios base compartida por todas las services. */
export const api = axios.create({ baseURL: '/api', withCredentials: true });

/** URL (relativa a baseURL) del endpoint de refresh. */
const REFRESH_PATH = '/auth/refresh';

interface RefreshResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
}

export interface SessionHandlers {
  /** Se invoca con el nuevo access token tras un refresh correcto (AuthContext setter). */
  onTokenRefreshed: (token: string) => void;
  /** Se invoca cuando el refresh falla: limpiar sesión + redirigir a login. */
  onSessionExpired: () => void;
}

let handlers: SessionHandlers | null = null;

/**
 * Registra los callbacks que conectan el interceptor con AuthContext y el routing.
 * Lo llama un componente dentro del Router (ver SessionBridge).
 */
export function registerSessionHandlers(h: SessionHandlers): void {
  handlers = h;
}

// ─── Single-flight refresh ────────────────────────────────────────────────────
// Una única promesa de refresh en vuelo compartida por todas las peticiones que
// reciben 401 casi a la vez. Se limpia al resolverse (éxito o fallo).
let refreshPromise: Promise<string> | null = null;

async function performRefresh(): Promise<string> {
  // Sin body: la cookie httpOnly `refresh_token` viaja sola (withCredentials).
  // `_skipAuthRefresh` evita que un 401 del propio refresh dispare otro refresh.
  const { data } = await api.post<RefreshResponse>(REFRESH_PATH, undefined, {
    _skipAuthRefresh: true,
  });
  // Propaga el nuevo token a AuthContext UNA sola vez por refresh real, aunque
  // varias peticiones concurrentes esperen esta misma promesa (single-flight).
  handlers?.onTokenRefreshed(data.access_token);
  return data.access_token;
}

function refreshOnce(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

function isRefreshRequest(config: InternalAxiosRequestConfig): boolean {
  return (config.url ?? '').includes(REFRESH_PATH);
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig | undefined;
    const status = error.response?.status;

    // Solo intervenimos en 401 de peticiones que NO son ya un reintento, ni el
    // propio refresh, ni endpoints de credenciales (opt-out explícito).
    if (
      status !== 401 ||
      !original ||
      original._retry ||
      original._skipAuthRefresh ||
      isRefreshRequest(original)
    ) {
      return Promise.reject(error);
    }

    original._retry = true;

    try {
      const newToken = await refreshOnce();
      // Reintenta la petición original con el nuevo access token.
      original.headers.set('Authorization', `Bearer ${newToken}`);
      return api(original);
    } catch (refreshError) {
      // Refresh caducado/revocado: sesión no recuperable → limpiar + login.
      handlers?.onSessionExpired();
      return Promise.reject(refreshError);
    }
  }
);
