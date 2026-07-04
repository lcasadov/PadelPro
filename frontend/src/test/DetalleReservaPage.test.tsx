// Grupo 7 — DetalleReservaPage (mockup 13) — TDD.
// Detalle desde GET /api/reservas/{id}. El botón cancelar solo es visible si el
// usuario es owner (userId === ownerId) y el estado es cancelable; oculto en caso
// contrario (D8). Cancelar → DELETE 204. Errores: 422 CANCELLATION_DEADLINE_PASSED
// (fuera de plazo, sin reembolso, RN-RES-04), 403 (ajena, RN-RGPD-03), 404.
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { DetalleReservaPage } from '../pages/DetalleReservaPage';
import { server } from './mocks/server';

afterEach(() => server.resetHandlers());

function authValue(userId: number | null) {
  return {
    accessToken: 'user.jwt.token',
    role: 'USER',
    mustChangePassword: false,
    userId,
    isAuthenticated: true,
    setAccessToken: () => {},
    setSession: () => {},
    clearMustChangePassword: () => {},
    loadUserId: async () => {},
  };
}

function renderPage(userId: number | null = 42, id = 'r-1') {
  return render(
    <AuthContext.Provider value={authValue(userId)}>
      <MemoryRouter initialEntries={[`/reservas/detalle/${id}`]}>
        <Routes>
          <Route path="/reservas/detalle/:id" element={<DetalleReservaPage />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

// Contrato real: la reserva trae `ownerId` (no `ownerName`) y los participantes
// usan `owner` (no `isOwner`) y `externalName` (no `nombre`), sin `id`/`joinedAt`.
function reserva(overrides: Record<string, unknown> = {}) {
  return {
    id: 'r-1',
    reservationDate: '2026-07-10',
    startTime: '20:00',
    endTime: '21:00',
    durationMinutes: 60,
    status: 'CONFIRMED',
    channel: 'WEB',
    ownerId: 42,
    priceTotal: 16,
    participants: [
      { userId: 42, externalName: null, externalPhone: null, slotPosition: 1, owner: true },
      { userId: null, externalName: 'Marcos R.', externalPhone: null, slotPosition: 2, owner: false },
    ],
    pago: { id: 'p-1', amount: 16, method: 'REDSYS', status: 'PAID', paidAt: '2026-07-04T10:00:00Z' },
    createdAt: '2026-07-04T10:00:00Z',
    ...overrides,
  };
}

function mockGetReserva(body: unknown, status = 200) {
  server.use(http.get('/api/reservas/r-1', () => HttpResponse.json(body, { status })));
}

describe('DetalleReservaPage (Grupo 7)', () => {
  it('7.1 — muestra los datos completos, el organizador y los participantes adicionales', async () => {
    mockGetReserva(reserva());
    renderPage(42);

    expect(await screen.findByText('20:00')).toBeInTheDocument();
    expect(screen.getByText('2026-07-10')).toBeInTheDocument();
    expect(screen.getByText(/16/)).toBeInTheDocument();
    // Organizador = participante con owner===true; "· Tú" porque userId (42) coincide.
    expect(screen.getByText(/Organizador · Tú/)).toBeInTheDocument();
    // Participante adicional (owner===false) por su externalName.
    expect(screen.getByText('Marcos R.')).toBeInTheDocument();
  });

  it('7.1b — organizador sin "· Tú" cuando el userId autenticado no coincide con el del owner', async () => {
    mockGetReserva(reserva());
    renderPage(99);

    await screen.findByText('20:00');
    expect(screen.getByText('Organizador')).toBeInTheDocument();
    expect(screen.queryByText(/Organizador · Tú/)).toBeNull();
  });

  it('7.2a — botón cancelar visible si owner y estado cancelable', async () => {
    mockGetReserva(reserva());
    renderPage(42);
    await screen.findByText('20:00');
    expect(screen.getByRole('button', { name: /cancelar/i })).toBeInTheDocument();
  });

  it('7.2b — botón cancelar oculto si no es owner', async () => {
    mockGetReserva(reserva());
    renderPage(99);
    await screen.findByText('20:00');
    expect(screen.queryByRole('button', { name: /cancelar/i })).toBeNull();
  });

  it('7.2c — botón cancelar oculto si el estado no es cancelable', async () => {
    mockGetReserva(reserva({ status: 'COMPLETED' }));
    renderPage(42);
    await screen.findByText('20:00');
    expect(screen.queryByRole('button', { name: /cancelar/i })).toBeNull();
  });

  it('7.3 — cancelación dentro de plazo (204) refleja la reserva como cancelada', async () => {
    mockGetReserva(reserva());
    server.use(http.delete('/api/reservas/r-1', () => new HttpResponse(null, { status: 204 })));
    renderPage(42);
    await screen.findByText('20:00');

    await userEvent.click(screen.getByRole('button', { name: /cancelar/i }));
    // Confirmación (RN-RES-04): panel con política.
    await userEvent.click(screen.getByRole('button', { name: /confirmar cancelaci/i }));

    await waitFor(() => expect(screen.getByText(/cancelada/i)).toBeInTheDocument());
  });

  it('7.4 — 422 CANCELLATION_DEADLINE_PASSED muestra fuera de plazo + sin reembolso', async () => {
    mockGetReserva(reserva());
    server.use(
      http.delete('/api/reservas/r-1', () =>
        HttpResponse.json(
          { error: 'CANCELLATION_DEADLINE_PASSED', message: 'tarde', timestamp: '2026-07-04T10:00:00Z' },
          { status: 422 }
        )
      )
    );
    renderPage(42);
    await screen.findByText('20:00');

    await userEvent.click(screen.getByRole('button', { name: /cancelar/i }));
    await userEvent.click(screen.getByRole('button', { name: /confirmar cancelaci/i }));

    expect(await screen.findByText(/fuera de plazo/i)).toBeInTheDocument();
    expect(screen.getByText(/no aplica reembolso|sin reembolso/i)).toBeInTheDocument();
  });

  it('7.5a — 403 (ajena) muestra acceso denegado sin exponer datos', async () => {
    mockGetReserva({ error: 'FORBIDDEN', message: 'No tiene acceso a esta reserva', timestamp: '2026-07-04T10:00:00Z' }, 403);
    renderPage(42);

    expect(await screen.findByText(/acceso denegado|no tienes acceso|no autorizado/i)).toBeInTheDocument();
    // No se filtran datos de la reserva ajena.
    expect(screen.queryByText('20:00')).toBeNull();
    expect(screen.queryByText('Marcos R.')).toBeNull();
  });

  it('7.5b — 404 (inexistente) muestra reserva no encontrada', async () => {
    mockGetReserva({ error: 'NOT_FOUND', message: 'no existe', timestamp: '2026-07-04T10:00:00Z' }, 404);
    renderPage(42);

    expect(await screen.findByText(/no encontrada|no existe|no se encontr/i)).toBeInTheDocument();
  });
});
