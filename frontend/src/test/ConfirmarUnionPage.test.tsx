// partidas-unirse (Grupo 4.3/4.4) — ConfirmarUnionPage (mockup 22) — TDD.
// Detalle de la partida (re-consultada del listado por reservaId) + "tu parte"
// informativo (priceTotal ÷ (participantes+1)) con aviso de pago presencial (sin
// pasarela). Unirse → POST /reservas/{id}/unirse. 200 → confirmación; 409 ya
// participante → mensaje sin checkout; 422 completa → mensaje.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { ConfirmarUnionPage } from '../pages/ConfirmarUnionPage';
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

const partida = {
  reservaId: 'r-9',
  reservationDate: '2026-07-10',
  startTime: '18:30',
  durationMinutes: 90,
  plazasLibres: 1,
  priceTotal: 18,
  participantes: [
    { nombre: 'Marcos R.', slotPosition: 1, owner: true },
    { nombre: 'Pablo G.', slotPosition: 2, owner: false },
    { nombre: 'Andrea S.', slotPosition: 3, owner: false },
  ],
};

function mockPartidas(partidas: unknown[]) {
  server.use(http.get('/api/partidas', () => HttpResponse.json(partidas)));
}

function renderPage(id = 'r-9', fecha = '2026-07-10') {
  return render(
    <AuthContext.Provider value={authValue}>
      <MemoryRouter initialEntries={[`/reservas/partidas/${id}/unirse?fecha=${fecha}`]}>
        <Routes>
          <Route path="/reservas/partidas/:id/unirse" element={<ConfirmarUnionPage />} />
          <Route path="/reservas/mias" element={<div>Mis reservas</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('ConfirmarUnionPage (Grupo 4.3/4.4)', () => {
  it('muestra el detalle y "tu parte" = total ÷ (participantes+1), con aviso de pago presencial', async () => {
    mockPartidas([partida]);
    renderPage();

    expect(await screen.findByText('18:30')).toBeInTheDocument();
    // 3 participantes → tras unirte 4 → 18 / 4 = 4,50 € (pill "Tu parte").
    expect(screen.getByTestId('tu-parte')).toHaveTextContent('4,50');
    // Total informativo de la partida.
    expect(screen.getByText(/18,00/)).toBeInTheDocument();
    // Aviso de pago presencial (sin pasarela).
    expect(screen.getByText(/presencial/i)).toBeInTheDocument();
  });

  it('unión exitosa (200) muestra confirmación', async () => {
    mockPartidas([partida]);
    server.use(
      http.post('/api/reservas/r-9/unirse', () =>
        HttpResponse.json(
          { participanteId: 'p-1', reservaId: 'r-9', userId: 42, nombre: 'Luis', statusPago: 'PENDING' },
          { status: 200 }
        )
      )
    );
    renderPage();
    await screen.findByText('18:30');

    await userEvent.click(screen.getByRole('button', { name: /unirme/i }));

    expect(await screen.findByText(/te has unido|unión confirmada|apuntado/i)).toBeInTheDocument();
  });

  it('409 (ya participante) muestra mensaje claro sin checkout', async () => {
    mockPartidas([partida]);
    server.use(
      http.post('/api/reservas/r-9/unirse', () =>
        HttpResponse.json({ error: 'ALREADY_PARTICIPANT', message: 'ya participas' }, { status: 409 })
      )
    );
    renderPage();
    await screen.findByText('18:30');

    await userEvent.click(screen.getByRole('button', { name: /unirme/i }));

    expect(await screen.findByRole('heading', { name: /ya estás en esta partida/i })).toBeInTheDocument();
    // No hay ningún flujo de pago/checkout.
    expect(screen.queryByText(/pagar|checkout|tarjeta/i)).toBeNull();
  });

  it('422 (partida completa) muestra mensaje de completa', async () => {
    mockPartidas([partida]);
    server.use(
      http.post('/api/reservas/r-9/unirse', () =>
        HttpResponse.json({ error: 'RESERVATION_FULL', message: 'completa' }, { status: 422 })
      )
    );
    renderPage();
    await screen.findByText('18:30');

    await userEvent.click(screen.getByRole('button', { name: /unirme/i }));

    expect(await screen.findByRole('heading', { name: /completa|sin plazas|no quedan plazas/i })).toBeInTheDocument();
  });

  it('partida ya no disponible en el listado muestra aviso', async () => {
    mockPartidas([]); // la partida ya no está abierta
    renderPage();
    expect(await screen.findByRole('heading', { name: /ya no (está|esta) disponible|no disponible|se ha completado/i })).toBeInTheDocument();
  });
});
