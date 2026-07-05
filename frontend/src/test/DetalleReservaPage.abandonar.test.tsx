// partidas-unirse (Grupo 4.4) — abandono de partida desde el detalle.
// Un participante no-owner que abre el detalle de una reserva en la que participa
// ve la acción "Abandonar partida" → DELETE /api/reservas/{id}/participacion (204).
// El owner NO ve la acción (debe cancelar). Tras abandonar, navega a mis reservas.
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

// Reserva con owner (id 7) y un participante no-owner (id 42).
function reserva(overrides: Record<string, unknown> = {}) {
  return {
    id: 'r-1',
    reservationDate: '2026-07-10',
    startTime: '20:00',
    endTime: '21:30',
    durationMinutes: 90,
    status: 'CONFIRMED',
    channel: 'WEB',
    ownerId: 7,
    priceTotal: 18,
    participants: [
      { userId: 7, externalName: null, externalPhone: null, slotPosition: 1, owner: true },
      { userId: 42, externalName: null, externalPhone: null, slotPosition: 2, owner: false },
    ],
    pago: null,
    createdAt: '2026-07-04T10:00:00Z',
    ...overrides,
  };
}

function mockGetReserva(body: unknown, status = 200) {
  server.use(http.get('/api/reservas/r-1', () => HttpResponse.json(body, { status })));
}

function renderPage(userId: number | null) {
  return render(
    <AuthContext.Provider value={authValue(userId)}>
      <MemoryRouter initialEntries={['/reservas/detalle/r-1']}>
        <Routes>
          <Route path="/reservas/detalle/:id" element={<DetalleReservaPage />} />
          <Route path="/reservas/mias" element={<div>Mis reservas</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('DetalleReservaPage — abandonar partida (Grupo 4.4)', () => {
  it('participante no-owner ve la acción "Abandonar partida"', async () => {
    mockGetReserva(reserva());
    renderPage(42);
    await screen.findByText('20:00');
    expect(screen.getByRole('button', { name: /abandonar/i })).toBeInTheDocument();
    // No es owner: no ve cancelar.
    expect(screen.queryByRole('button', { name: /cancelar reserva/i })).toBeNull();
  });

  it('el owner NO ve "Abandonar" (debe cancelar)', async () => {
    mockGetReserva(reserva());
    renderPage(7);
    await screen.findByText('20:00');
    expect(screen.queryByRole('button', { name: /abandonar/i })).toBeNull();
    expect(screen.getByRole('button', { name: /cancelar reserva/i })).toBeInTheDocument();
  });

  it('abandonar (204) navega a mis reservas', async () => {
    mockGetReserva(reserva());
    server.use(
      http.delete('/api/reservas/r-1/participacion', () => new HttpResponse(null, { status: 204 }))
    );
    renderPage(42);
    await screen.findByText('20:00');

    await userEvent.click(screen.getByRole('button', { name: /abandonar/i }));
    // Confirmación en dos pasos.
    await userEvent.click(screen.getByRole('button', { name: /confirmar abandono/i }));

    await waitFor(() => expect(screen.getByText('Mis reservas')).toBeInTheDocument());
  });
});
