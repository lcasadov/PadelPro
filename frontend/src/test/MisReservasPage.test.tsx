// Grupo 6 — MisReservasPage (mockup 05) — TDD.
// Lista GET /api/reservas con estado de reserva y estado de pago. Estado vacío
// con acceso a buscar disponibilidad.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { MisReservasPage } from '../pages/MisReservasPage';
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

function renderPage() {
  return render(
    <AuthContext.Provider value={authValue}>
      <MemoryRouter initialEntries={['/reservas/mias']}>
        <Routes>
          <Route path="/reservas/mias" element={<MisReservasPage />} />
          <Route path="/reservas/disponibilidad" element={<div>Buscar disponibilidad</div>} />
          <Route path="/reservas/detalle/:id" element={<div>Detalle</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

const reservas = [
  {
    id: 'r-1',
    reservationDate: '2026-07-10',
    startTime: '20:00',
    durationMinutes: 60,
    status: 'CONFIRMED',
    channel: 'WEB',
    ownerId: 42,
    priceTotal: 16,
    participants: [],
    pago: { id: 'p-1', reservaId: 'r-1', amount: 16, status: 'PAID', createdAt: '2026-07-04T10:00:00Z' },
    createdAt: '2026-07-04T10:00:00Z',
  },
  {
    id: 'r-2',
    reservationDate: '2026-07-12',
    startTime: '18:00',
    durationMinutes: 60,
    status: 'PENDING_CONFIRMATION',
    channel: 'WEB',
    ownerId: 42,
    priceTotal: 14,
    participants: [],
    pago: { id: 'p-2', reservaId: 'r-2', amount: 14, status: 'PENDING', createdAt: '2026-07-04T10:00:00Z' },
    createdAt: '2026-07-04T10:00:00Z',
  },
];

describe('MisReservasPage (Grupo 6)', () => {
  it('6.1 — lista reservas con estado de reserva y estado de pago', async () => {
    server.use(
      http.get('/api/reservas', () => HttpResponse.json({ data: reservas, totalElements: 2 }))
    );
    renderPage();

    // Reservas listadas (hora de cada tramo).
    expect(await screen.findByText('20:00')).toBeInTheDocument();
    expect(screen.getByText('18:00')).toBeInTheDocument();

    // Estado de reserva.
    expect(screen.getByText(/confirmada/i)).toBeInTheDocument();
    expect(screen.getByText(/pendiente de confirmaci/i)).toBeInTheDocument();

    // Estado de pago.
    expect(screen.getByText(/pagado/i)).toBeInTheDocument();
    expect(screen.getByText(/pago pendiente/i)).toBeInTheDocument();
  });

  it('6.2 — estado vacío con acceso a buscar disponibilidad', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json({ data: [], totalElements: 0 })));
    renderPage();

    expect(await screen.findByText(/no tienes reservas|sin reservas|todavía no/i)).toBeInTheDocument();

    // Acceso a buscar disponibilidad.
    await userEvent.click(screen.getByRole('link', { name: /buscar|disponibilidad|reservar/i }));
    expect(await screen.findByText('Buscar disponibilidad')).toBeInTheDocument();
  });
});
