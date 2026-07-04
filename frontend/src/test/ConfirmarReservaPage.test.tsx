// Grupo 5 — ConfirmarReservaPage (mockup 04) — TDD.
// Muestra fecha/hora/duración elegidas (query params) y el precio total DEVUELTO
// por el backend (nunca calculado en cliente). Idempotency-Key generada por
// intento: reutilizada en reintento sin cambios, regenerada al cambiar datos (D2).
// Éxito (201, PENDING_CONFIRMATION) → "reserva pendiente de confirmación".
// Errores por code: 409 CONFLICT (D5), 400 VALIDATION_ERROR, 422 límites/estado.
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { ConfirmarReservaPage } from '../pages/ConfirmarReservaPage';
import { server } from './mocks/server';

afterEach(() => server.resetHandlers());

const authValue = {
  accessToken: 'user.jwt.token',
  role: 'USER',
  mustChangePassword: false,
  userId: 42,
  isAuthenticated: true,
  setAccessToken: () => {},
  setSession: () => {},
  clearMustChangePassword: () => {},
  loadUserId: async () => {},
};

function renderPage(query = '?fecha=2026-07-10&hora=20:00&duracion=60') {
  return render(
    <AuthContext.Provider value={authValue}>
      <MemoryRouter initialEntries={[`/reservas/confirmar${query}`]}>
        <Routes>
          <Route path="/reservas/confirmar" element={<ConfirmarReservaPage />} />
          <Route path="/reservas/disponibilidad" element={<div>Buscar disponibilidad</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

function reservaResponse(overrides: Record<string, unknown> = {}) {
  return {
    id: 'r-1',
    reservationDate: '2026-07-10',
    startTime: '20:00',
    durationMinutes: 60,
    status: 'PENDING_CONFIRMATION',
    channel: 'WEB',
    ownerId: 42,
    priceTotal: 16,
    participants: [],
    pago: null,
    createdAt: '2026-07-04T10:00:00Z',
    ...overrides,
  };
}

describe('ConfirmarReservaPage (Grupo 5)', () => {
  it('5.1 — muestra fecha/hora/duración elegidas y el precio total del backend', async () => {
    server.use(http.post('/api/reservas', () => HttpResponse.json(reservaResponse(), { status: 201 })));
    renderPage();

    // Resumen del tramo elegido (query params).
    expect(screen.getByText(/20:00/)).toBeInTheDocument();
    expect(screen.getByText(/60/)).toBeInTheDocument();
    expect(screen.getByText(/2026-07-10/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /confirmar|reservar/i }));

    // El precio total proviene de la respuesta del backend (priceTotal: 16).
    await waitFor(() => expect(screen.getByText(/16/)).toBeInTheDocument());
  });

  it('5.2 — añadir participantes adicionales los incluye en el payload', async () => {
    let capturedBody: any = null;
    server.use(
      http.post('/api/reservas', async ({ request }) => {
        capturedBody = await request.json();
        return HttpResponse.json(reservaResponse(), { status: 201 });
      })
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /añadir|compañ|participante/i }));
    const nameInput = screen.getByLabelText(/nombre.*compañ|participante|invitado/i);
    await userEvent.type(nameInput, 'Marcos R.');

    await userEvent.click(screen.getByRole('button', { name: /confirmar|reservar/i }));

    await waitFor(() => expect(capturedBody).not.toBeNull());
    expect(capturedBody.participantesAdicionales).toEqual([{ externalName: 'Marcos R.' }]);
    // Nunca se envía importe desde el cliente (RN-RES-03).
    expect(capturedBody.priceTotal).toBeUndefined();
    expect(capturedBody.price).toBeUndefined();
  });

  it('5.3 — éxito (201 PENDING_CONFIRMATION) muestra pendiente de confirmación sin pago', async () => {
    server.use(http.post('/api/reservas', () => HttpResponse.json(reservaResponse(), { status: 201 })));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /confirmar|reservar/i }));

    expect(await screen.findByText(/pendiente de confirmaci/i)).toBeInTheDocument();
    expect(screen.queryByText(/pagar|pago online|redsys/i)).toBeNull();
  });

  it('5.4 — 409 CONFLICT muestra "la franja se acaba de ocupar" + botón volver/refrescar', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json({ code: 'CONFLICT', message: 'ocupado', errors: [] }, { status: 409 })
      )
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /confirmar|reservar/i }));

    expect(await screen.findByText(/se acaba de ocupar/i)).toBeInTheDocument();
    // Botón para volver a la búsqueda / refrescar disponibilidad (D5).
    await userEvent.click(screen.getByRole('button', { name: /volver|refrescar|buscar/i }));
    expect(await screen.findByText('Buscar disponibilidad')).toBeInTheDocument();
  });

  it('5.5a — 422 PARTICIPANTS_LIMIT_EXCEEDED muestra mensaje de límite de participantes', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          { code: 'PARTICIPANTS_LIMIT_EXCEEDED', message: 'demasiados', errors: [] },
          { status: 422 }
        )
      )
    );
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /confirmar|reservar/i }));
    expect(await screen.findByText(/máximo.*participante|participante.*máximo|superado/i)).toBeInTheDocument();
  });

  it('5.5b — 400 VALIDATION_ERROR muestra mensaje específico', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          {
            code: 'VALIDATION_ERROR',
            message: 'inválido',
            errors: [{ field: 'startTime', message: 'La hora no es válida' }],
          },
          { status: 400 }
        )
      )
    );
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /confirmar|reservar/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/no es válid|inválid|revisa los datos/i);
  });

  it('5.5c — 422 INVALID_STATE_TRANSITION muestra mensaje de estado inválido', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json(
          { code: 'INVALID_STATE_TRANSITION', message: 'estado', errors: [] },
          { status: 422 }
        )
      )
    );
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /confirmar|reservar/i }));
    expect(await screen.findByText(/no es válida para el estado|estado actual/i)).toBeInTheDocument();
  });

  it('5.6 — Idempotency-Key se reutiliza en reintento sin cambios y se regenera al cambiar datos', async () => {
    const keys: string[] = [];
    let failNext = true;
    server.use(
      http.post('/api/reservas', ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key') ?? '');
        if (failNext) {
          failNext = false;
          return HttpResponse.json({ code: 'UNKNOWN', message: 'boom', errors: [] }, { status: 500 });
        }
        return HttpResponse.json(reservaResponse(), { status: 201 });
      })
    );
    renderPage();

    // Primer intento → falla (500), aparece opción de reintentar.
    await userEvent.click(screen.getByRole('button', { name: /confirmar|reservar/i }));
    const retryBtn = await screen.findByRole('button', { name: /reintentar|confirmar|reservar/i });

    // Reintento SIN cambios → misma key.
    await userEvent.click(retryBtn);
    await waitFor(() => expect(keys.length).toBe(2));
    expect(keys[0]).toBe(keys[1]);
    expect(keys[0]).not.toBe('');
  });

  it('5.6b — cambiar datos del formulario regenera la Idempotency-Key', async () => {
    const keys: string[] = [];
    server.use(
      http.post('/api/reservas', ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key') ?? '');
        return HttpResponse.json({ code: 'UNKNOWN', message: 'boom', errors: [] }, { status: 500 });
      })
    );
    renderPage();

    // Intento 1.
    await userEvent.click(screen.getByRole('button', { name: /confirmar|reservar/i }));
    await waitFor(() => expect(keys.length).toBe(1));

    // Cambiar datos del formulario (añadir participante) → nueva key.
    await userEvent.click(screen.getByRole('button', { name: /añadir|compañ|participante/i }));
    await userEvent.type(screen.getByLabelText(/nombre.*compañ|participante|invitado/i), 'Nuevo');
    await userEvent.click(screen.getByRole('button', { name: /reintentar|confirmar|reservar/i }));

    await waitFor(() => expect(keys.length).toBe(2));
    expect(keys[0]).not.toBe(keys[1]);
  });
});
