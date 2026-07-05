// pagos-redsys-online (Grupo 6, 6.3) — MisReservasPage "pagar ahora" — TDD.
// Activa el botón "pagar ahora" (antes deshabilitado): solo visible para el owner
// con pago PENDING. Al pulsar, llama a POST /api/pagos/iniciar y navega al checkout
// pasando el response en el state de navegación.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
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

// Stub del checkout: expone el redsysUrl recibido por el state para verificar que la
// navegación propaga el response de iniciar pago.
function CheckoutStub() {
  const location = useLocation();
  const state = location.state as { pago?: { redsysUrl?: string } } | null;
  return <div>Checkout · {state?.pago?.redsysUrl ?? 'sin-datos'}</div>;
}

function renderPage(userId: number | null = 42) {
  return render(
    <AuthContext.Provider value={authValue(userId)}>
      <MemoryRouter initialEntries={['/reservas/mias']}>
        <Routes>
          <Route path="/reservas/mias" element={<MisReservasPage />} />
          <Route path="/pagos/checkout" element={<CheckoutStub />} />
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

const INICIAR_OK = {
  pagoId: 'p-1',
  redsysOrderId: '0001abc',
  redsysUrl: 'https://sis-t.redsys.es/sis/realizarPago',
  amount: 16,
  status: 'IN_PROGRESS',
  dsSignatureVersion: 'HMAC_SHA256_V1',
  dsMerchantParameters: 'base64params',
  dsSignature: 'firma',
};

describe('MisReservasPage — "pagar ahora" (Grupo 6)', () => {
  it('owner con pago PENDING ve "Pagar ahora" activo', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json([reserva()])));
    renderPage();

    await screen.findByText('20:00');
    const btn = screen.getByRole('button', { name: /pagar ahora/i });
    expect(btn).toBeEnabled();
    expect(btn).not.toHaveTextContent(/próximamente/i);
  });

  it('al pulsar "Pagar ahora" llama a iniciar pago y navega al checkout con el response', async () => {
    let capturedBody: Record<string, unknown> = {};
    server.use(
      http.get('/api/reservas', () => HttpResponse.json([reserva()])),
      http.post('/api/pagos/iniciar', async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(INICIAR_OK, { status: 200 });
      })
    );
    renderPage();

    await screen.findByText('20:00');
    await userEvent.click(screen.getByRole('button', { name: /pagar ahora/i }));

    expect(await screen.findByText(/checkout ·/i)).toHaveTextContent(INICIAR_OK.redsysUrl);
    expect(capturedBody).toEqual({ reservaId: 'r-1' });
  });

  it('no muestra "Pagar ahora" a quien no es owner', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json([reserva()])));
    renderPage(99);

    await screen.findByText('20:00');
    expect(screen.queryByRole('button', { name: /pagar ahora/i })).toBeNull();
  });

  it('no muestra "Pagar ahora" si el pago no está PENDING (IN_PROGRESS)', async () => {
    server.use(
      http.get('/api/reservas', () =>
        HttpResponse.json([
          reserva({ pago: { id: 'p-1', reservaId: 'r-1', amount: 16, status: 'IN_PROGRESS', createdAt: 't' } }),
        ])
      )
    );
    renderPage();

    await screen.findByText('20:00');
    expect(screen.queryByRole('button', { name: /pagar ahora/i })).toBeNull();
  });

  it('muestra error si iniciar pago falla con 409 (pago ya en curso)', async () => {
    server.use(
      http.get('/api/reservas', () => HttpResponse.json([reserva()])),
      http.post('/api/pagos/iniciar', () =>
        HttpResponse.json({ error: 'CONFLICT', message: 'Ya hay un pago activo.', timestamp: 't' }, { status: 409 })
      )
    );
    renderPage();

    await screen.findByText('20:00');
    await userEvent.click(screen.getByRole('button', { name: /pagar ahora/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/ya está en curso/i);
  });
});
