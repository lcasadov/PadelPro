// reservas-ui-jugador-fixes (Grupo 5, D6) — MisReservasPage acciones inline — TDD.
//  - Cancelar desde la lista (DELETE) refresca el estado.
//  - Estado de pago visible por tarjeta.
//  - "Pago en diferido" no dispara pago; "pagar ahora" deshabilitado/informativo.
//  - Acciones ocultas por estado (reserva cancelada / no owner).
//  - Control "Inicio" navega a Home.
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
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

function renderPage(userId: number | null = 42) {
  return render(
    <AuthContext.Provider value={authValue(userId)}>
      <MemoryRouter initialEntries={['/reservas/mias']}>
        <Routes>
          <Route path="/reservas/mias" element={<MisReservasPage />} />
          <Route path="/home" element={<div>Pantalla de inicio</div>} />
          <Route path="/reservas/disponibilidad" element={<div>Buscar disponibilidad</div>} />
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

describe('MisReservasPage — acciones (Grupo 5)', () => {
  it('cancelar desde la lista (DELETE) refresca el estado a cancelada', async () => {
    let estado = 'CONFIRMED';
    server.use(
      http.get('/api/reservas', () =>
        HttpResponse.json([reserva({ status: estado })])
      ),
      http.delete('/api/reservas/r-1', () => {
        estado = 'CANCELLED';
        return new HttpResponse(null, { status: 204 });
      })
    );
    renderPage();

    await screen.findByText('20:00');
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    await userEvent.click(screen.getByRole('button', { name: /confirmar cancelaci/i }));

    await waitFor(() => expect(screen.getByText(/cancelada/i)).toBeInTheDocument());
    // Tras cancelar, la acción de cancelar ya no se ofrece.
    expect(screen.queryByRole('button', { name: 'Cancelar' })).toBeNull();
  });

  it('muestra el estado de pago de cada tarjeta', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json([reserva()])));
    renderPage();
    expect(await screen.findByText(/pago pendiente/i)).toBeInTheDocument();
  });

  it('"pago en diferido" informa cobro presencial sin llamar a ningún endpoint', async () => {
    let pagoCalled = false;
    server.use(
      http.get('/api/reservas', () => HttpResponse.json([reserva()])),
      http.post('/api/pagos', () => {
        pagoCalled = true;
        return HttpResponse.json({}, { status: 201 });
      })
    );
    renderPage();

    await screen.findByText('20:00');
    await userEvent.click(screen.getByRole('button', { name: /pago en diferido/i }));

    expect(await screen.findByText(/presencial/i)).toBeInTheDocument();
    expect(pagoCalled).toBe(false);
  });

  // pagos-redsys-online (Grupo 6): "pagar ahora" ya NO está deshabilitado; se activa
  // para el owner con pago PENDING. El flujo completo (iniciar + navegar al checkout)
  // se cubre en MisReservasPage.pagar.test.tsx.
  it('"pagar ahora" está activo para el owner con pago PENDING', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json([reserva()])));
    renderPage();

    await screen.findByText('20:00');
    const btn = screen.getByRole('button', { name: /pagar ahora/i });
    expect(btn).toBeEnabled();
    expect(btn).not.toHaveTextContent(/próximamente/i);
  });

  it('oculta acciones cuando la reserva no es cancelable (CANCELLED)', async () => {
    server.use(
      http.get('/api/reservas', () =>
        HttpResponse.json([reserva({ status: 'CANCELLED', pago: { id: 'p', amount: 16, status: 'REFUNDED', createdAt: 't' } })])
      )
    );
    renderPage();

    await screen.findByText('20:00');
    expect(screen.queryByRole('button', { name: 'Cancelar' })).toBeNull();
    expect(screen.queryByRole('button', { name: /pagar ahora/i })).toBeNull();
  });

  it('oculta "Cancelar" cuando el usuario no es owner', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json([reserva()])));
    renderPage(99);

    await screen.findByText('20:00');
    expect(screen.queryByRole('button', { name: 'Cancelar' })).toBeNull();
  });

  it('el control "Inicio" navega a Home', async () => {
    server.use(http.get('/api/reservas', () => HttpResponse.json([reserva()])));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /inicio/i }));
    expect(await screen.findByText('Pantalla de inicio')).toBeInTheDocument();
  });
});
