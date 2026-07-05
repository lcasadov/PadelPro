// pagos-redsys-online (Grupo 6) — pagosApi — TDD contra MSW.
//  - iniciarPago: POST /api/pagos/iniciar con Bearer + { reservaId }, devuelve el
//    form firmado (redsysUrl + los tres campos ds*); mapea 403/404/409/422.
//  - getPagos: GET /api/pagos (array plano) con y sin token (fail-safe de retorno TPV).
import { describe, it, expect, afterEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import { iniciarPago, getPagos } from '../services/pagosApi';
import { ReservaApiError, isReservaApiError } from '../services/reservasApi';

const TOKEN = 'user.jwt.token';

afterEach(() => server.resetHandlers());

const INICIAR_OK = {
  pagoId: 'p-1',
  redsysOrderId: '0001abc',
  redsysUrl: 'https://sis-t.redsys.es:25443/sis/realizarPago',
  amount: 16,
  status: 'IN_PROGRESS',
  dsSignatureVersion: 'HMAC_SHA256_V1',
  dsMerchantParameters: 'eyJEc19NZXJjaGFudF9BbW91bnQiOiIxNjAwIn0=',
  dsSignature: 'firma-base64-abc',
};

describe('pagosApi — iniciarPago', () => {
  it('POST /api/pagos/iniciar con Bearer y { reservaId }, devuelve el form firmado', async () => {
    let capturedAuth = '';
    let capturedBody: Record<string, unknown> = {};
    server.use(
      http.post('/api/pagos/iniciar', async ({ request }) => {
        capturedAuth = request.headers.get('Authorization') ?? '';
        capturedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(INICIAR_OK, { status: 200 });
      })
    );

    const resp = await iniciarPago(TOKEN, 'r-1');

    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    expect(capturedBody).toEqual({ reservaId: 'r-1' });
    // El cliente nunca envía importe (RN-RES-03): lo congela el backend.
    expect(capturedBody).not.toHaveProperty('amount');
    expect(resp.redsysUrl).toContain('redsys');
    expect(resp.dsSignatureVersion).toBe('HMAC_SHA256_V1');
    expect(resp.dsMerchantParameters).toBe(INICIAR_OK.dsMerchantParameters);
    expect(resp.dsSignature).toBe('firma-base64-abc');
    expect(resp.status).toBe('IN_PROGRESS');
  });

  it('mapea 403 (no owner)', async () => {
    server.use(
      http.post('/api/pagos/iniciar', () =>
        HttpResponse.json(
          { error: 'FORBIDDEN', message: 'No eres el propietario.', timestamp: 't' },
          { status: 403 }
        )
      )
    );
    const err = await iniciarPago(TOKEN, 'r-1').catch((e) => e);
    expect(isReservaApiError(err)).toBe(true);
    expect(err).toBeInstanceOf(ReservaApiError);
    expect(err.code).toBe('FORBIDDEN');
    expect(err.status).toBe(403);
  });

  it('mapea 404 (reserva inexistente)', async () => {
    server.use(
      http.post('/api/pagos/iniciar', () =>
        HttpResponse.json({ error: 'NOT_FOUND', message: 'No existe.', timestamp: 't' }, { status: 404 })
      )
    );
    const err = await iniciarPago(TOKEN, 'nope').catch((e) => e);
    expect(err.code).toBe('NOT_FOUND');
    expect(err.status).toBe(404);
  });

  it('mapea 409 (pago ya en curso o pagado)', async () => {
    server.use(
      http.post('/api/pagos/iniciar', () =>
        HttpResponse.json(
          { error: 'CONFLICT', message: 'Ya hay un pago activo.', timestamp: 't' },
          { status: 409 }
        )
      )
    );
    const err = await iniciarPago(TOKEN, 'r-1').catch((e) => e);
    expect(err.code).toBe('CONFLICT');
    expect(err.status).toBe(409);
  });

  it('mapea 422 (reserva cancelada)', async () => {
    server.use(
      http.post('/api/pagos/iniciar', () =>
        HttpResponse.json(
          { error: 'INVALID_STATE_TRANSITION', message: 'Reserva cancelada.', timestamp: 't' },
          { status: 422 }
        )
      )
    );
    const err = await iniciarPago(TOKEN, 'r-1').catch((e) => e);
    expect(err.code).toBe('INVALID_STATE_TRANSITION');
    expect(err.status).toBe(422);
  });
});

describe('pagosApi — getPagos', () => {
  it('GET /api/pagos con Bearer devuelve el array plano', async () => {
    let capturedAuth = '';
    server.use(
      http.get('/api/pagos', ({ request }) => {
        capturedAuth = request.headers.get('Authorization') ?? '';
        return HttpResponse.json([
          { id: 'p-1', reservaId: 'r-1', amount: 16, method: 'REDSYS', status: 'PAID', paidAt: 't' },
        ]);
      })
    );

    const result = await getPagos(TOKEN);
    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    expect(result).toHaveLength(1);
    expect(result[0].status).toBe('PAID');
  });

  it('normaliza un cuerpo paginado { data: [...] } (fail-safe)', async () => {
    server.use(
      http.get('/api/pagos', () =>
        HttpResponse.json({ data: [{ id: 'p-2', reservaId: 'r-2', amount: 20, status: 'FAILED' }] })
      )
    );
    const result = await getPagos(TOKEN);
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('p-2');
  });

  it('sin token no envía header Authorization (retorno del TPV)', async () => {
    let hadAuth = true;
    server.use(
      http.get('/api/pagos', ({ request }) => {
        hadAuth = request.headers.has('Authorization');
        return HttpResponse.json([]);
      })
    );
    await getPagos(null);
    expect(hadAuth).toBe(false);
  });
});
