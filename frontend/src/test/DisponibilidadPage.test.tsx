// Grupo 4 — DisponibilidadPage (mockup 03) — TDD.
// Selector de fecha → lista de tramos desde GET /api/reservas/disponibles.
// La acción "Reservar" solo se ofrece en tramos creable === true (D7, fail-safe:
// creable ausente/false ⇒ no se ofrece crear). Estado vacío sin revelar
// mantenimiento. Seleccionar un tramo creable navega a Confirmar con
// fecha + hora + duración.
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { DisponibilidadPage } from '../pages/DisponibilidadPage';
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

// Marcador para la ruta de confirmación: refleja los query params recibidos.
function ConfirmarStub() {
  const [params] = useSearchParams();
  return (
    <div>
      <span data-testid="c-fecha">{params.get('fecha')}</span>
      <span data-testid="c-hora">{params.get('hora')}</span>
      <span data-testid="c-duracion">{params.get('duracion')}</span>
    </div>
  );
}

function renderPage() {
  return render(
    <AuthContext.Provider value={authValue}>
      <MemoryRouter initialEntries={['/reservas/disponibilidad']}>
        <Routes>
          <Route path="/reservas/disponibilidad" element={<DisponibilidadPage />} />
          <Route path="/reservas/confirmar" element={<ConfirmarStub />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

function mockDisponibilidad(tramos: unknown[]) {
  server.use(
    http.get('/api/reservas/disponibles', ({ request }) => {
      const fecha = new URL(request.url).searchParams.get('fecha') ?? '';
      return HttpResponse.json({ fecha, tramosDisponibles: tramos });
    })
  );
}

const tramoCreable = { horaInicio: '20:00', duracionMinutos: 60, plazasLibres: 4, creable: true };
const tramoParcial = { horaInicio: '18:00', duracionMinutos: 60, plazasLibres: 2, creable: false };

describe('DisponibilidadPage (Grupo 4)', () => {
  beforeEach(() => {
    mockDisponibilidad([tramoCreable, tramoParcial]);
  });

  it('4.1 — renderiza selector de fecha y lista los tramos del service', async () => {
    renderPage();
    expect(screen.getByLabelText(/fecha/i)).toBeInTheDocument();
    expect(await screen.findByText('20:00')).toBeInTheDocument();
    expect(screen.getByText('18:00')).toBeInTheDocument();
  });

  it('4.2 — solo los tramos creable:true ofrecen la acción de reservar', async () => {
    renderPage();
    await screen.findByText('20:00');

    // El tramo creable ofrece "Reservar".
    expect(screen.getByRole('button', { name: /reservar.*20:00/i })).toBeInTheDocument();
    // El tramo parcial (creable:false) NO ofrece crear.
    expect(screen.queryByRole('button', { name: /reservar.*18:00/i })).toBeNull();
  });

  it('4.3 — estado vacío cuando no hay tramos (sin revelar mantenimiento)', async () => {
    mockDisponibilidad([]);
    renderPage();
    expect(await screen.findByText(/no hay|sin disponibilidad|no quedan/i)).toBeInTheDocument();
    expect(screen.queryByText(/mantenimiento/i)).toBeNull();
  });

  it('4.4 — seleccionar un tramo creable navega a Confirmar con fecha+hora+duración', async () => {
    renderPage();
    // Fijar una fecha determinista.
    const dateInput = screen.getByLabelText(/fecha/i) as HTMLInputElement;
    fireEvent.change(dateInput, { target: { value: '2026-07-10' } });

    const row = (await screen.findByText('20:00')).closest('li, tr, div[data-tramo]') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: /reservar/i }));

    await waitFor(() => {
      expect(screen.getByTestId('c-fecha')).toHaveTextContent('2026-07-10');
      expect(screen.getByTestId('c-hora')).toHaveTextContent('20:00');
      expect(screen.getByTestId('c-duracion')).toHaveTextContent('60');
    });
  });
});
