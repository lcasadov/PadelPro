// pagos-simulador-gestion (4.1 + 3.2) — MisReservasPage.
// Cubre: el badge de estado de pago (Pendiente/Pagado) se muestra de forma
// persistente (leído de reserva.pago.status del backend), y "pagar ahora" enruta
// al simulador (no al Redsys real) con reservaId + importe.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation, useSearchParams } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { MisReservasPage } from '../pages/MisReservasPage';
import { server } from './mocks/server';

afterEach(() => server.resetHandlers());

function authValue(userId: number | null = 42) {
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

// Stub del simulador: expone reservaId+importe recibidos (por query y por state).
function SimuladorStub() {
  const location = useLocation();
  const [params] = useSearchParams();
  const state = location.state as { reservaId?: string; importe?: number } | null;
  return (
    <div>
      Simulador · query={params.get('reservaId')}:{params.get('importe')} · state=
      {state?.reservaId}:{String(state?.importe)}
    </div>
  );
}

function renderPage(userId: number | null = 42) {
  return render(
    <AuthContext.Provider value={authValue(userId)}>
      <MemoryRouter initialEntries={['/reservas/mias']}>
        <Routes>
          <Route path="/reservas/mias" element={<MisReservasPage />} />
          <Route path="/pagos/simulador" element={<SimuladorStub />} />
          <Route path="/home" element={<div>Home</div>} />
          <Route path="/reservas/detalle/:id" element={<div>Detalle</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

function reserva(overrides: Record<string, unknown> = {}) {
  return {
    id: 'r-1',
    reservationDate: '2026-07-10',
    startTime: '20:00',
    durationMinutes: 60,
    status: 'CONFIRMED',
    channel: 'WEB',
    ownerId: 42,
    priceTotal: 16,
    participants: [],
    pago: { id: 'p-1', reservaId: 'r-1', amount: 16, status: 'PENDING', createdAt: 't' },
    createdAt: 't',
    ...overrides,
  };
}

describe('MisReservasPage — estado de pago persistente y simulador', () => {
  it('muestra el badge "Pago pendiente" para una reserva con pago PENDING', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json([reserva()])));
    renderPage();
    await screen.findByText('20:00');
    expect(screen.getByText(/pago pendiente/i)).toBeInTheDocument();
  });

  it('muestra el badge "Pagado" para una reserva con pago PAID (estado persistente)', async () => {
    server.use(
      http.get('/api/reservas', () =>
        HttpResponse.json([
          reserva({ pago: { id: 'p-1', reservaId: 'r-1', amount: 16, status: 'PAID', createdAt: 't' } }),
        ])
      )
    );
    renderPage();
    await screen.findByText('20:00');
    expect(screen.getByText(/^pagado$/i)).toBeInTheDocument();
    // Una reserva ya pagada no ofrece "pagar ahora".
    expect(screen.queryByRole('button', { name: /pagar ahora/i })).toBeNull();
  });

  it('"pagar ahora" enruta al simulador con reservaId e importe', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json([reserva()])));
    renderPage();

    await screen.findByText('20:00');
    await userEvent.click(screen.getByRole('button', { name: /pagar ahora/i }));

    const stub = await screen.findByText(/simulador ·/i);
    expect(stub).toHaveTextContent('query=r-1:16');
    expect(stub).toHaveTextContent('state=r-1:16');
  });

  it('"pago en diferido" aclara que la reserva sigue Pendiente', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json([reserva()])));
    renderPage();

    await screen.findByText('20:00');
    await userEvent.click(screen.getByRole('button', { name: /pago en diferido/i }));

    expect(await screen.findByRole('status')).toHaveTextContent(/pendiente/i);
  });
});
