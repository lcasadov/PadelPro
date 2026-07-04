// reservas-ui-jugador (Grupo 2) — reservasApi (TDD RED) — validado contra MSW.
// Cubre: disponibilidad (con flag `creable`), crear reserva (Idempotency-Key +
// ausencia de importe, RN-RES-03), listar, detalle, cancelar (204) y el mapeo
// del error shape real { code, message, errors[] } a errores tipados por `code`.
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
            startTime: '09:00',
            durationMinutes: 60,
            status: 'PENDING_CONFIRMATION',
            channel: 'WEB',
            ownerId: 5,
            ownerName: 'John Doe',
            priceTotal: 15.0,
            participants: [
              { id: 1, userId: 5, nombre: 'John Doe', statusPago: 'PENDING', isOwner: true, joinedAt: '2025-06-14T18:00:00Z' },
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
    expect(created.participants[0].isOwner).toBe(true);
  });

  it('crearReserva mapea 409 CONFLICT', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          { code: 'CONFLICT', message: 'La franja horaria ya está reservada.', errors: [] },
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

  it('crearReserva mapea 400 VALIDATION_ERROR con errors[] de campo', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          {
            code: 'VALIDATION_ERROR',
            message: 'Los datos de la solicitud no son válidos.',
            errors: [{ field: 'startTime', message: 'La hora debe ser en punto o en media hora.' }],
          },
          { status: 400 }
        )
      )
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('VALIDATION_ERROR');
    expect(err.fieldErrors).toHaveLength(1);
    expect(err.fieldErrors[0]).toMatchObject({ field: 'startTime' });
  });

  it('crearReserva mapea 422 PARTICIPANTS_LIMIT_EXCEEDED', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          { code: 'PARTICIPANTS_LIMIT_EXCEEDED', message: 'Se ha superado el número máximo de participantes.', errors: [] },
          { status: 422 }
        )
      )
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('PARTICIPANTS_LIMIT_EXCEEDED');
    expect(err.status).toBe(422);
  });

  it('crearReserva mapea 422 INVALID_STATE_TRANSITION', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          { code: 'INVALID_STATE_TRANSITION', message: 'Operación no válida para el estado actual.', errors: [] },
          { status: 422 }
        )
      )
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('INVALID_STATE_TRANSITION');
  });
});

describe('reservasApi — listar y detalle', () => {
  it('getMisReservas hace GET /reservas y devuelve la lista paginada', async () => {
    let capturedAuth = '';
    server.use(
      http.get('/api/reservas', ({ request }) => {
        capturedAuth = request.headers.get('Authorization') ?? '';
        return HttpResponse.json({
          data: [
            {
              id: '550e8400-e29b-41d4-a716-446655440000',
              reservationDate: '2025-06-15',
              startTime: '09:00',
              durationMinutes: 60,
              status: 'CONFIRMED',
              channel: 'WEB',
              ownerId: 5,
              priceTotal: 15.0,
              participants: [],
              createdAt: '2025-06-14T18:00:00Z',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          size: 20,
        });
      })
    );

    const result = await getMisReservas(TOKEN);
    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    expect(result.data).toHaveLength(1);
    expect(result.data[0].status).toBe('CONFIRMED');
  });

  it('getReserva hace GET /reservas/{id}', async () => {
    server.use(
      http.get('/api/reservas/abc-1', () =>
        HttpResponse.json({
          id: 'abc-1',
          reservationDate: '2025-06-15',
          startTime: '09:00',
          durationMinutes: 60,
          status: 'CONFIRMED',
          channel: 'WEB',
          ownerId: 5,
          priceTotal: 15.0,
          participants: [{ id: 1, userId: 5, nombre: 'John', statusPago: 'PAID', isOwner: true, joinedAt: '2025-06-14T18:00:00Z' }],
          createdAt: '2025-06-14T18:00:00Z',
        })
      )
    );

    const result = await getReserva(TOKEN, 'abc-1');
    expect(result.id).toBe('abc-1');
    expect(result.participants[0].isOwner).toBe(true);
  });

  it('getReserva mapea 403 (reserva ajena, RN-RGPD-03)', async () => {
    server.use(
      http.get('/api/reservas/ajena', () =>
        HttpResponse.json({ code: 'FORBIDDEN', message: 'No tiene permisos.', errors: [] }, { status: 403 })
      )
    );

    const err = await getReserva(TOKEN, 'ajena').catch((e) => e);
    expect(err.code).toBe('FORBIDDEN');
    expect(err.status).toBe(403);
  });

  it('getReserva mapea 404 (inexistente)', async () => {
    server.use(
      http.get('/api/reservas/nope', () =>
        HttpResponse.json({ code: 'NOT_FOUND', message: 'No existe.', errors: [] }, { status: 404 })
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

  it('cancelarReserva mapea 422 CANCELLATION_DEADLINE_PASSED', async () => {
    server.use(
      http.delete('/api/reservas/tarde', () =>
        HttpResponse.json(
          { code: 'CANCELLATION_DEADLINE_PASSED', message: 'Fuera de plazo, no aplica reembolso.', errors: [] },
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
        HttpResponse.json({ code: 'FORBIDDEN', message: 'No tiene permisos.', errors: [] }, { status: 403 })
      )
    );

    const err = await cancelarReserva(TOKEN, 'ajena').catch((e) => e);
    expect(err.code).toBe('FORBIDDEN');
  });
});
