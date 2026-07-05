// partidas-unirse (Grupo 4.1/4.2) — PartidasAbiertasPage (mockup 21) — TDD.
// Selector de fecha → lista de partidas abiertas desde GET /api/partidas?fecha=.
// Cada partida muestra hora, duración y plazas libres; estado vacío y error
// neutros. Seleccionar una partida navega a "Confirmar unión" con su reservaId y
// la fecha (para que la confirmación re-consulte el listado).
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes, useParams, useSearchParams } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { PartidasAbiertasPage } from '../pages/PartidasAbiertasPage';
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

// Marcador de la ruta de confirmación: refleja el reservaId y la fecha recibidos.
function ConfirmarUnionStub() {
  const { id } = useParams<{ id: string }>();
  const [params] = useSearchParams();
  return (
    <div>
      <span data-testid="u-id">{id}</span>
      <span data-testid="u-fecha">{params.get('fecha')}</span>
    </div>
  );
}

function renderPage() {
  return render(
    <AuthContext.Provider value={authValue}>
      <MemoryRouter initialEntries={['/reservas/partidas']}>
        <Routes>
          <Route path="/reservas/partidas" element={<PartidasAbiertasPage />} />
          <Route path="/reservas/partidas/:id/unirse" element={<ConfirmarUnionStub />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

function mockPartidas(partidas: unknown[], status = 200) {
  server.use(http.get('/api/partidas', () => HttpResponse.json(partidas, { status })));
}

const partidaA = {
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
const partidaB = {
  reservaId: 'r-10',
  reservationDate: '2026-07-10',
  startTime: '20:00',
  durationMinutes: 60,
  plazasLibres: 2,
  priceTotal: 16,
  participantes: [{ nombre: 'Carla L.', slotPosition: 1, owner: true }],
};

describe('PartidasAbiertasPage (Grupo 4.1/4.2)', () => {
  it('lista las partidas abiertas con hora, duración y plazas libres', async () => {
    mockPartidas([partidaA, partidaB]);
    renderPage();

    expect(screen.getByLabelText(/fecha/i)).toBeInTheDocument();
    expect(await screen.findByText('18:30')).toBeInTheDocument();
    expect(screen.getByText('20:00')).toBeInTheDocument();
    // Plazas libres visibles (falta 1 / faltan 2).
    expect(screen.getByText(/falta 1/i)).toBeInTheDocument();
    expect(screen.getByText(/faltan 2/i)).toBeInTheDocument();
  });

  it('estado vacío cuando no hay partidas abiertas', async () => {
    mockPartidas([]);
    renderPage();
    expect(await screen.findByText(/no hay partidas|sin partidas|no quedan/i)).toBeInTheDocument();
  });

  it('estado de error cuando el listado falla', async () => {
    mockPartidas({ error: 'SERVER_ERROR', message: 'boom' }, 500);
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudo|inténtalo/i);
  });

  it('seleccionar una partida navega a confirmar unión con reservaId + fecha', async () => {
    mockPartidas([partidaA, partidaB]);
    renderPage();
    const dateInput = screen.getByLabelText(/fecha/i) as HTMLInputElement;
    fireEvent.change(dateInput, { target: { value: '2026-07-10' } });

    const card = (await screen.findByText('18:30')).closest('[data-partida]') as HTMLElement;
    await userEvent.click(within(card).getByRole('button', { name: /unirme/i }));

    await waitFor(() => {
      expect(screen.getByTestId('u-id')).toHaveTextContent('r-9');
      expect(screen.getByTestId('u-fecha')).toHaveTextContent('2026-07-10');
    });
  });
});
