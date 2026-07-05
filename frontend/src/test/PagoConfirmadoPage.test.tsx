// pagos-redsys-online (Grupo 6, D6) — PagoConfirmadoPage — TDD.
// La página de retorno del TPV NO confía en la URL: consulta GET /api/pagos y
// muestra el estado real del pago de la reserva (por reservaId de la query):
//   - PAID → éxito con código de reserva + referencia Redsys + importe.
//   - FAILED → pago no completado.
//   - IN_PROGRESS/PENDING (o pago no encontrado) → "procesando" con Actualizar
//     (el webhook puede tardar); al pulsar, re-consulta y refleja el nuevo estado.
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { PagoConfirmadoPage } from '../pages/PagoConfirmadoPage';
import { server } from './mocks/server';

afterEach(() => server.resetHandlers());

function authValue() {
  return {
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
}

function renderPage(query = '?reservaId=r-1') {
  return render(
    <AuthContext.Provider value={authValue()}>
      <MemoryRouter initialEntries={[`/pagos/ok${query}`]}>
        <Routes>
          <Route path="/pagos/ok" element={<PagoConfirmadoPage />} />
          <Route path="/reservas/mias" element={<div>Mis reservas</div>} />
          <Route path="/reservas/detalle/:id" element={<div>Detalle reserva</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('PagoConfirmadoPage — estado real por backend (no confía en la URL)', () => {
  it('PAID: muestra confirmación con código de reserva, referencia Redsys e importe', async () => {
    server.use(
      http.get('/api/pagos', () =>
        HttpResponse.json([
          {
            id: 'p-1',
            reservaId: 'r-1',
            amount: 16,
            method: 'REDSYS',
            status: 'PAID',
            paidAt: '2026-07-05T10:00:00Z',
            transactionId: 'AUTH-998877',
          },
        ])
      )
    );
    renderPage();

    expect(await screen.findByText(/reservada/i)).toBeInTheDocument();
    expect(screen.getByText('r-1')).toBeInTheDocument();
    expect(screen.getByText('AUTH-998877')).toBeInTheDocument();
    expect(screen.getByText(/16,00\s*€/)).toBeInTheDocument();
    expect(screen.getByText('✓ Confirmado')).toBeInTheDocument();
  });

  it('FAILED: informa de pago no completado', async () => {
    server.use(
      http.get('/api/pagos', () =>
        HttpResponse.json([{ id: 'p-1', reservaId: 'r-1', amount: 16, status: 'FAILED' }])
      )
    );
    renderPage();

    expect(await screen.findByText(/no se ha completado/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /mis reservas/i })).toBeInTheDocument();
  });

  it('IN_PROGRESS: muestra "procesando" y al Actualizar re-consulta y refleja PAID', async () => {
    let estado = 'IN_PROGRESS';
    server.use(
      http.get('/api/pagos', () =>
        HttpResponse.json([{ id: 'p-1', reservaId: 'r-1', amount: 16, status: estado }])
      )
    );
    renderPage();

    expect(await screen.findByText(/procesando/i)).toBeInTheDocument();

    // El webhook confirma; al refrescar, la página lo refleja.
    estado = 'PAID';
    await userEvent.click(screen.getByRole('button', { name: /actualizar/i }));

    await waitFor(() => expect(screen.getByText(/reservada/i)).toBeInTheDocument());
  });

  it('pago aún no encontrado en el historial → "procesando" (webhook pendiente)', async () => {
    server.use(http.get('/api/pagos', () => HttpResponse.json([])));
    renderPage();
    expect(await screen.findByText(/procesando/i)).toBeInTheDocument();
  });
});
