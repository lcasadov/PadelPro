// reservas-ui-jugador-fixes — ConfirmarReservaPage — TDD de los grupos 1-4.
//  G1: 401 AUTH_REQUIRED (tras refresh fallido) → redirección a login.
//  G2: selector de duración 60/90/120 (default 60, filtrado por maxDuracion).
//  G3: payload XOR socio (userId) vs externo (externalName[+phone]); bloqueo si
//      un participante está incompleto.
//  G4: control "Inicio" navega a Home.
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
          <Route path="/login" element={<div>Pantalla de login</div>} />
          <Route path="/home" element={<div>Pantalla de inicio</div>} />
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

describe('ConfirmarReservaPage — Grupo 1 (sesión caducada)', () => {
  it('401 AUTH_REQUIRED (refresh fallido) redirige a login', async () => {
    server.use(
      http.post('/api/reservas', () =>
        HttpResponse.json({ error: 'AUTH_REQUIRED', message: 'token', timestamp: 't' }, { status: 401 })
      ),
      http.post('/api/auth/refresh', () => HttpResponse.json({ error: 'AUTH_REQUIRED' }, { status: 401 }))
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /confirmar reserva/i }));

    expect(await screen.findByText('Pantalla de login')).toBeInTheDocument();
  });
});

describe('ConfirmarReservaPage — Grupo 2 (duración)', () => {
  it('elegir 90 min envía durationMinutes:90 y lo refleja en el resumen', async () => {
    let capturedBody: Record<string, unknown> | null = null;
    server.use(
      http.post('/api/reservas', async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(reservaResponse({ durationMinutes: 90 }), { status: 201 });
      })
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: '90 min' }));
    expect(screen.getByTestId('resumen-duracion')).toHaveTextContent('90 min');

    await userEvent.click(screen.getByRole('button', { name: /confirmar reserva/i }));
    await waitFor(() => expect(capturedBody).not.toBeNull());
    expect(capturedBody!.durationMinutes).toBe(90);
  });

  it('default 60 min cuando no se elige duración', async () => {
    let capturedBody: Record<string, unknown> | null = null;
    server.use(
      http.post('/api/reservas', async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(reservaResponse(), { status: 201 });
      })
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /confirmar reserva/i }));
    await waitFor(() => expect(capturedBody).not.toBeNull());
    expect(capturedBody!.durationMinutes).toBe(60);
  });

  it('maxDuracion=90 no ofrece la opción de 120 min', async () => {
    renderPage('?fecha=2026-07-10&hora=20:00&duracion=60&maxDuracion=90');

    expect(screen.getByRole('button', { name: '60 min' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '90 min' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '120 min' })).toBeNull();
  });
});

describe('ConfirmarReservaPage — Grupo 3 (participante XOR)', () => {
  it('socio registrado → payload con userId y sin externalName', async () => {
    let capturedBody: Record<string, unknown> | null = null;
    server.use(
      http.get('/api/usuarios/buscar', () => HttpResponse.json([{ id: 7, nombre: 'Ana García' }])),
      http.post('/api/reservas', async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(reservaResponse(), { status: 201 });
      })
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /añadir compañero/i }));
    await userEvent.click(screen.getByRole('button', { name: /socio registrado/i }));
    await userEvent.type(screen.getByLabelText(/buscar socio/i), 'Ana');
    await userEvent.click(await screen.findByRole('button', { name: 'Ana García' }));

    await userEvent.click(screen.getByRole('button', { name: /confirmar reserva/i }));
    await waitFor(() => expect(capturedBody).not.toBeNull());
    expect(capturedBody!.participantesAdicionales).toEqual([{ userId: 7 }]);
  });

  it('invitado externo → payload con externalName (+phone) y sin userId', async () => {
    let capturedBody: Record<string, unknown> | null = null;
    server.use(
      http.post('/api/reservas', async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(reservaResponse(), { status: 201 });
      })
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /añadir compañero/i }));
    await userEvent.type(screen.getByLabelText(/nombre del compañero/i), 'Marcos R.');
    await userEvent.type(screen.getByLabelText(/teléfono/i), '600111222');

    await userEvent.click(screen.getByRole('button', { name: /confirmar reserva/i }));
    await waitFor(() => expect(capturedBody).not.toBeNull());
    expect(capturedBody!.participantesAdicionales).toEqual([
      { externalName: 'Marcos R.', externalPhone: '600111222' },
    ]);
  });

  it('participante incompleto (externo sin nombre) bloquea el submit', async () => {
    let called = false;
    server.use(
      http.post('/api/reservas', () => {
        called = true;
        return HttpResponse.json(reservaResponse(), { status: 201 });
      })
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /añadir compañero/i }));
    // No se rellena el nombre → submit bloqueado.
    await userEvent.click(screen.getByRole('button', { name: /confirmar reserva|reintentar/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/completa los datos/i);
    expect(called).toBe(false);
  });
});

describe('ConfirmarReservaPage — Grupo 4 (navegación)', () => {
  it('el control "Inicio" navega a Home', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /inicio/i }));
    expect(await screen.findByText('Pantalla de inicio')).toBeInTheDocument();
  });
});
