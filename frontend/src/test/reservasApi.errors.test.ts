// reservas-ui-jugador-fixes (Grupo 1, D2) — mapeo de errores al contrato real.
// El backend emite `{ error, message, timestamp, details? }`. Se verifica que:
//  - 401 → AUTH_REQUIRED (sesión), diferenciado del genérico.
//  - 400 VALIDATION_ERROR con `details` → el detalle queda disponible en `.details`.
//  - 5xx → SERVER_ERROR (servidor), distinto de un error de datos.
//  - error de red (sin respuesta) → NETWORK_ERROR.
import { describe, it, expect, afterEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import { crearReserva, type CrearReservaPayload } from '../services/reservasApi';

const TOKEN = 'user.jwt.token';
const payload: CrearReservaPayload = {
  reservationDate: '2026-07-10',
  startTime: '20:00',
  durationMinutes: 60,
};

afterEach(() => server.resetHandlers());

describe('reservasApi — mapeo de errores (Grupo 1)', () => {
  it('401 AUTH_REQUIRED se mapea a code AUTH_REQUIRED (tras fallar el refresh)', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json({ error: 'AUTH_REQUIRED', message: 'token', timestamp: 't' }, { status: 401 })
      ),
      // El interceptor intentará refrescar; el refresh también falla (401).
      http.post('/api/auth/refresh', () => HttpResponse.json({ error: 'AUTH_REQUIRED' }, { status: 401 }))
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('AUTH_REQUIRED');
    expect(err.status).toBe(401);
  });

  it('400 VALIDATION_ERROR con details expone el primer detalle en .details', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          {
            error: 'VALIDATION_ERROR',
            message: 'Datos inválidos',
            details: ['startTime debe estar en punto o y media'],
            timestamp: 't',
          },
          { status: 400 }
        )
      )
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('VALIDATION_ERROR');
    expect(err.details[0]).toContain('startTime');
  });

  it('5xx se mapea a SERVER_ERROR (no a error de datos)', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json({ error: 'INTERNAL', message: 'boom', timestamp: 't' }, { status: 500 })
      )
    );

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('SERVER_ERROR');
    expect(err.status).toBe(500);
  });

  it('error de red (sin respuesta) se mapea a NETWORK_ERROR', async () => {
    server.use(http.post('/api/reservas', () => HttpResponse.error()));

    const err = await crearReserva(TOKEN, payload, 'k').catch((e) => e);
    expect(err.code).toBe('NETWORK_ERROR');
    expect(err.status).toBe(0);
  });
});
