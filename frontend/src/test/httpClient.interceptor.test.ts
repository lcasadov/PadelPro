// auth-session-refresh (Grupo 2, TDD Red) — interceptor de renovación silenciosa.
//
// Verifica el contrato del spec "Renovación silenciosa de sesión en el cliente":
//  - 401 en petición autenticada → POST /api/auth/refresh → reintento de la
//    original con el NUEVO access token → éxito, sin intervención del usuario.
//  - Refresh 401 → limpia sesión (onSessionExpired) y NO reintenta refresh (sin bucle).
//  - Varios 401 concurrentes → UN ÚNICO /api/auth/refresh (single-flight) → todas
//    las peticiones se reintentan al resolverse.
//
// El interceptor se conecta con AuthContext/routing mediante `registerSessionHandlers`
// (callbacks onTokenRefreshed / onSessionExpired). El módulo axios no conoce React.
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import { api, registerSessionHandlers } from '../services/httpClient';

const OLD = 'old.access.token';
const NEW = 'new.access.token';

let onTokenRefreshed: ReturnType<typeof vi.fn>;
let onSessionExpired: ReturnType<typeof vi.fn>;

beforeEach(() => {
  onTokenRefreshed = vi.fn();
  onSessionExpired = vi.fn();
  registerSessionHandlers({ onTokenRefreshed, onSessionExpired });
});

afterEach(() => server.resetHandlers());

describe('interceptor de refresh — renovación transparente ante 401', () => {
  it('401 → /auth/refresh → reintenta la original con el nuevo token → éxito', async () => {
    let refreshCalls = 0;
    let protectedAuthOnSuccess = '';
    server.use(
      http.get('/api/protegido', ({ request }) => {
        const auth = request.headers.get('Authorization') ?? '';
        if (auth === `Bearer ${NEW}`) {
          protectedAuthOnSuccess = auth;
          return HttpResponse.json({ ok: true });
        }
        return HttpResponse.json({ error: 'AUTH_REQUIRED' }, { status: 401 });
      }),
      http.post('/api/auth/refresh', () => {
        refreshCalls += 1;
        return HttpResponse.json({ access_token: NEW, token_type: 'Bearer', expires_in: 900 });
      })
    );

    const { data } = await api.get('/protegido', {
      headers: { Authorization: `Bearer ${OLD}` },
    });

    expect(data).toEqual({ ok: true });
    expect(refreshCalls).toBe(1);
    expect(protectedAuthOnSuccess).toBe(`Bearer ${NEW}`);
    expect(onTokenRefreshed).toHaveBeenCalledWith(NEW);
    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it('refresh 401 → limpia sesión (onSessionExpired) y NO entra en bucle sobre /auth/refresh', async () => {
    let refreshCalls = 0;
    server.use(
      http.get('/api/protegido', () =>
        HttpResponse.json({ error: 'AUTH_REQUIRED' }, { status: 401 })
      ),
      http.post('/api/auth/refresh', () => {
        refreshCalls += 1;
        return HttpResponse.json({ error: 'AUTH_REQUIRED' }, { status: 401 });
      })
    );

    const err = await api
      .get('/protegido', { headers: { Authorization: `Bearer ${OLD}` } })
      .catch((e) => e);

    expect(err).toBeInstanceOf(Error);
    // Un único intento de refresh: la respuesta 401 de /auth/refresh NO dispara otro refresh.
    expect(refreshCalls).toBe(1);
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
    expect(onTokenRefreshed).not.toHaveBeenCalled();
  });
});

describe('interceptor de refresh — single-flight ante 401 concurrentes', () => {
  it('varios 401 simultáneos → un solo /auth/refresh → todas reintentadas con el nuevo token', async () => {
    let refreshCalls = 0;
    server.use(
      http.get('/api/a', ({ request }) =>
        request.headers.get('Authorization') === `Bearer ${NEW}`
          ? HttpResponse.json({ from: 'a' })
          : HttpResponse.json({ error: 'AUTH_REQUIRED' }, { status: 401 })
      ),
      http.get('/api/b', ({ request }) =>
        request.headers.get('Authorization') === `Bearer ${NEW}`
          ? HttpResponse.json({ from: 'b' })
          : HttpResponse.json({ error: 'AUTH_REQUIRED' }, { status: 401 })
      ),
      http.get('/api/c', ({ request }) =>
        request.headers.get('Authorization') === `Bearer ${NEW}`
          ? HttpResponse.json({ from: 'c' })
          : HttpResponse.json({ error: 'AUTH_REQUIRED' }, { status: 401 })
      ),
      http.post('/api/auth/refresh', async () => {
        refreshCalls += 1;
        // Pequeña latencia para forzar solapamiento real de las 3 peticiones.
        await new Promise((r) => setTimeout(r, 20));
        return HttpResponse.json({ access_token: NEW, token_type: 'Bearer', expires_in: 900 });
      })
    );

    const headers = { Authorization: `Bearer ${OLD}` };
    const [ra, rb, rc] = await Promise.all([
      api.get('/a', { headers }),
      api.get('/b', { headers }),
      api.get('/c', { headers }),
    ]);

    expect(refreshCalls).toBe(1);
    expect(ra.data).toEqual({ from: 'a' });
    expect(rb.data).toEqual({ from: 'b' });
    expect(rc.data).toEqual({ from: 'c' });
    expect(onTokenRefreshed).toHaveBeenCalledTimes(1);
    expect(onSessionExpired).not.toHaveBeenCalled();
  });
});

describe('interceptor de refresh — opt-out de endpoints de credenciales', () => {
  it('un 401 en una petición marcada _skipAuthRefresh NO dispara refresh', async () => {
    let refreshCalls = 0;
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ error: 'AUTH_INVALID_CREDENTIALS' }, { status: 401 })
      ),
      http.post('/api/auth/refresh', () => {
        refreshCalls += 1;
        return HttpResponse.json({ access_token: NEW, token_type: 'Bearer', expires_in: 900 });
      })
    );

    const err = await api
      .post('/auth/login', { email: 'x', password: 'y' }, { _skipAuthRefresh: true })
      .catch((e) => e);

    expect(err).toBeInstanceOf(Error);
    expect(refreshCalls).toBe(0);
    expect(onSessionExpired).not.toHaveBeenCalled();
  });
});
