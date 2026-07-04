// reservas-ui-jugador (Grupo 2) — reservasApi — validado contra MSW usando el
// CONTRATO REAL del backend (verificado en vivo):
//  - GET /api/reservas devuelve un ARRAY JSON plano (no `{ data: [...] }`).
//  - Cuerpo de error `{ error: "<CODE>", message, timestamp }` (clave `error`, no `code`).
//  - Errores de negocio con HTTP 422 (PARTICIPANTS_LIMIT_EXCEEDED,
//    INVALID_STATE_TRANSITION, CANCELLATION_DEADLINE_PASSED).
//  - Participante `{ userId, externalName, externalPhone, slotPosition, owner }`.
// Cubre: disponibilidad (flag `creable`), crear (Idempotency-Key + ausencia de
// importe, RN-RES-03), listar, detalle, cancelar (204) y el mapeo de errores.
import { describe, it, expect, afterEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import {
  getDisponibilidad,
  crearReserva,
  getMisReservas,
  getReserva,
  cancelarReserva,
  ReservaApiError,
  isReservaApiError,
  type CrearReservaPayload,
} from '../services/reservasApi';

const TOKEN = 'user.jwt.token';

afterEach(() => server.resetHandlers());

describe('reservasApi — disponibilidad', () => {
  it('getDisponibilidad hits GET /reservas/disponibles?fecha=... con Bearer y devuelve tramos con `creable`', async () => {
    let capturedUrl = '';
    let capturedAuth = '';
    server.use(
      http.get('/api/reservas/disponibles', ({ request }) => {
        capturedUrl = request.url;
        capturedAuth = request.headers.get('Authorization') ?? '';
        return HttpResponse.json({
          fecha: '2025-06-15',
          tramosDisponibles: [
            { horaInicio: '09:00', duracionMinutos: 60, plazasLibres: 4, creable: true },
            { horaInicio: '10:00', duracionMinutos: 60, plazasLibres: 2, creable: false },
          ],
        });
      })
    );

    const result = await getDisponibilidad(TOKEN, '2025-06-15');

    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    expect(capturedUrl).toContain('fecha=2025-06-15');
    expect(result.fecha).toBe('2025-06-15');
    expect(result.tramosDisponibles).toHaveLength(2);
    expect(result.tramosDisponibles[0].creable).toBe(true);
    expect(result.tramosDisponibles[1].creable).toBe(false);
    expect(result.tramosDisponibles[0].horaInicio).toBe('09:00');
  });
});

describe('reservasApi — crear reserva', () => {
  const payload: CrearReservaPayload = {
    reservationDate: '2025-06-15',
    startTime: '09:00',
    durationMinutes: 60,
    participantesAdicionales: [{ externalName: 'Carlos García' }],
    notes: 'Partido de entrenamiento',
  };

  it('crearReserva envía POST /reservas con Idempotency-Key y SIN importe (RN-RES-03)', async () => {
    let capturedKey = '';
    let capturedBody: Record<string, unknown> = {};
    let capturedAuth = '';
    server.use(
      http.post('/api/reservas', async ({ request }) => {
        capturedKey = request.headers.get('Idempotency-Key') ?? '';
        capturedAuth = request.headers.get('Authorization') ?? '';
        capturedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          {
            id: '550e8400-e29b-41d4-a716-446655440000',
            reservationDate: '2025-06-15',
            startTime: '09:00:00',
            endTime: '10:00:00',
            durationMinutes: 60,
            status: 'PENDING_CONFIRMATION',
            channel: 'WEB',
            ownerId: 5,
            priceTotal: 15.0,
            participants: [
              { userId: 5, externalName: null, externalPhone: null, slotPosition: 1, owner: true },
            ],
            pago: null,
            createdAt: '2025-06-14T18:00:00Z',
          },
          { status: 201 }
        );
      })
    );

    const created = await crearReserva(TOKEN, payload, 'idem-key-123');

    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    expect(capturedKey).toBe('idem-key-123');
    // El cliente NUNCA envía importe (RN-RES-03).
    expect(capturedBody).not.toHaveProperty('priceTotal');
    expect(capturedBody).not.toHaveProperty('price');
    expect(capturedBody).not.toHaveProperty('importe');
    expect(capturedBody).not.toHaveProperty('amount');
    expect(capturedBody).toMatchObject({
      reservationDate: '2025-06-15',
      startTime: '09:00',
      durationMinutes: 60,
    });
    expect(created.id).toBe('550e8400-e29b-41d4-a716-446655440000');
    expect(created.status).toBe('PENDING_CONFIRMATION');
    expect(created.priceTotal).toBe(15.0);
    expect(created.participants[0].owner).toBe(true);
  });

  it('crearReserva mapea 409 CONFLICT', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          { error: 'CONFLICT', message: 'La franja horaria ya está reservada.', timestamp: '2026-07-04T10:00:00Z' },
          { status: 409 }
        )
      )
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(isReservaApiError(err)).toBe(true);
    expect(err).toBeInstanceOf(ReservaApiError);
    expect(err.code).toBe('CONFLICT');
    expect(err.status).toBe(409);
  });

  it('crearReserva mapea 400 VALIDATION_ERROR (cuerpo real { error, message }, sin errors[])', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          {
            error: 'VALIDATION_ERROR',
            message: 'reservationDate es obligatorio (formato YYYY-MM-DD)',
            timestamp: '2026-07-04T10:00:00Z',
          },
          { status: 400 }
        )
      )
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('VALIDATION_ERROR');
    expect(err.status).toBe(400);
    expect(err.message).toContain('reservationDate');
  });

  it('crearReserva mapea 422 PARTICIPANTS_LIMIT_EXCEEDED (por el valor de `error`, no por status)', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          { error: 'PARTICIPANTS_LIMIT_EXCEEDED', message: 'Se ha superado el número máximo de participantes.', timestamp: '2026-07-04T10:00:00Z' },
          { status: 422 }
        )
      )
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('PARTICIPANTS_LIMIT_EXCEEDED');
    expect(err.status).toBe(422);
  });

  it('crearReserva mapea 422 INVALID_STATE_TRANSITION (por el valor de `error`, no por status)', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          { error: 'INVALID_STATE_TRANSITION', message: 'La reserva ya se encuentra en estado CANCELLED', timestamp: '2026-07-04T10:00:00Z' },
          { status: 422 }
        )
      )
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('INVALID_STATE_TRANSITION');
    expect(err.status).toBe(422);
  });
});

describe('reservasApi — listar y detalle', () => {
  it('getMisReservas hace GET /reservas y devuelve un ARRAY JSON plano (contrato real)', async () => {
    let capturedAuth = '';
    server.use(
      http.get('/api/reservas', ({ request }) => {
        capturedAuth = request.headers.get('Authorization') ?? '';
        return HttpResponse.json([
          {
            id: '550e8400-e29b-41d4-a716-446655440000',
            reservationDate: '2025-06-15',
            startTime: '09:00:00',
            endTime: '10:00:00',
            durationMinutes: 60,
            status: 'CONFIRMED',
            channel: 'WEB',
            ownerId: 5,
            priceTotal: 15.0,
            participants: [],
            createdAt: '2025-06-14T18:00:00Z',
          },
        ]);
      })
    );

    const result = await getMisReservas(TOKEN);
    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    expect(Array.isArray(result)).toBe(true);
    expect(result).toHaveLength(1);
    expect(result[0].status).toBe('CONFIRMED');
  });

  it('getMisReservas normaliza también un cuerpo paginado { data: [...] } (fail-safe)', async () => {
    server.use(
      http.get('/api/reservas', () =>
        HttpResponse.json({
          data: [
            {
              id: 'r-x',
              reservationDate: '2025-06-15',
              startTime: '11:00:00',
              durationMinutes: 60,
              status: 'PENDING_CONFIRMATION',
              channel: 'WEB',
              ownerId: 5,
              priceTotal: 15.0,
              participants: [],
              createdAt: '2025-06-14T18:00:00Z',
            },
          ],
        })
      )
    );

    const result = await getMisReservas(TOKEN);
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('r-x');
  });

  it('getReserva hace GET /reservas/{id} (participante con `owner`/`externalName`)', async () => {
    server.use(
      http.get('/api/reservas/abc-1', () =>
        HttpResponse.json({
          id: 'abc-1',
          reservationDate: '2025-06-15',
          startTime: '09:00:00',
          endTime: '10:00:00',
          durationMinutes: 60,
          status: 'CONFIRMED',
          channel: 'WEB',
          ownerId: 5,
          priceTotal: 15.0,
          participants: [{ userId: 5, externalName: null, externalPhone: null, slotPosition: 1, owner: true }],
          createdAt: '2025-06-14T18:00:00Z',
        })
      )
    );

    const result = await getReserva(TOKEN, 'abc-1');
    expect(result.id).toBe('abc-1');
    expect(result.participants[0].owner).toBe(true);
  });

  it('getReserva mapea 403 (reserva ajena, RN-RGPD-03)', async () => {
    server.use(
      http.get('/api/reservas/ajena', () =>
        HttpResponse.json({ error: 'FORBIDDEN', message: 'No tiene acceso a esta reserva', timestamp: '2026-07-04T10:00:00Z' }, { status: 403 })
      )
    );

    const err = await getReserva(TOKEN, 'ajena').catch((e) => e);
    expect(err.code).toBe('FORBIDDEN');
    expect(err.status).toBe(403);
  });

  it('getReserva mapea 404 (inexistente)', async () => {
    server.use(
      http.get('/api/reservas/nope', () =>
        HttpResponse.json({ error: 'NOT_FOUND', message: 'No existe.', timestamp: '2026-07-04T10:00:00Z' }, { status: 404 })
      )
    );

    const err = await getReserva(TOKEN, 'nope').catch((e) => e);
    expect(err.code).toBe('NOT_FOUND');
    expect(err.status).toBe(404);
  });
});

describe('reservasApi — cancelar', () => {
  it('cancelarReserva hace DELETE /reservas/{id} y resuelve con 204', async () => {
    let hit = false;
    let capturedAuth = '';
    server.use(
      http.delete('/api/reservas/abc-1', ({ request }) => {
        hit = true;
        capturedAuth = request.headers.get('Authorization') ?? '';
        return new HttpResponse(null, { status: 204 });
      })
    );

    await cancelarReserva(TOKEN, 'abc-1');
    expect(hit).toBe(true);
    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
  });

  it('cancelarReserva mapea 422 CANCELLATION_DEADLINE_PASSED (por el valor de `error`, no por status)', async () => {
    server.use(
      http.delete('/api/reservas/tarde', () =>
        HttpResponse.json(
          { error: 'CANCELLATION_DEADLINE_PASSED', message: 'Fuera de plazo, no aplica reembolso.', timestamp: '2026-07-04T10:00:00Z' },
          { status: 422 }
        )
      )
    );

    const err = await cancelarReserva(TOKEN, 'tarde').catch((e) => e);
    expect(err.code).toBe('CANCELLATION_DEADLINE_PASSED');
    expect(err.status).toBe(422);
  });

  it('cancelarReserva mapea 403 (no owner)', async () => {
    server.use(
      http.delete('/api/reservas/ajena', () =>
        HttpResponse.json({ error: 'FORBIDDEN', message: 'No tiene acceso a esta reserva', timestamp: '2026-07-04T10:00:00Z' }, { status: 403 })
      )
    );

    const err = await cancelarReserva(TOKEN, 'ajena').catch((e) => e);
    expect(err.code).toBe('FORBIDDEN');
  });
});
