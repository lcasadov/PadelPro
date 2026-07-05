// reservas-ui-jugador-fixes (Grupo 4) — navegación a Inicio desde las páginas de
// reservas. Disponibilidad y Detalle exponen un control visible "Inicio" que lleva
// a Home. (Confirmar y Mis Reservas se cubren en sus propias suites.)
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { DisponibilidadPage } from '../pages/DisponibilidadPage';
import { DetalleReservaPage } from '../pages/DetalleReservaPage';
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

describe('Navegación a Inicio (Grupo 4)', () => {
  it('Disponibilidad: "Inicio" navega a Home', async () => {
    server.use(
      http.get('/api/reservas/disponibles', () =>
        HttpResponse.json({ fecha: '2026-07-10', tramosDisponibles: [] })
      )
    );
    render(
      <AuthContext.Provider value={authValue}>
        <MemoryRouter initialEntries={['/reservas/disponibilidad']}>
          <Routes>
            <Route path="/reservas/disponibilidad" element={<DisponibilidadPage />} />
            <Route path="/home" element={<div>Pantalla de inicio</div>} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    );

    await userEvent.click(screen.getByRole('button', { name: /inicio/i }));
    expect(await screen.findByText('Pantalla de inicio')).toBeInTheDocument();
  });

  it('Detalle: "Inicio" navega a Home', async () => {
    server.use(
      http.get('/api/reservas/r-1', () =>
        HttpResponse.json({
          id: 'r-1',
          reservationDate: '2026-07-10',
          startTime: '20:00',
          endTime: '21:00',
          durationMinutes: 60,
          status: 'CONFIRMED',
          channel: 'WEB',
          ownerId: 42,
          priceTotal: 16,
          participants: [{ userId: 42, externalName: null, externalPhone: null, slotPosition: 1, owner: true }],
          pago: null,
          createdAt: 't',
        })
      )
    );
    render(
      <AuthContext.Provider value={authValue}>
        <MemoryRouter initialEntries={['/reservas/detalle/r-1']}>
          <Routes>
            <Route path="/reservas/detalle/:id" element={<DetalleReservaPage />} />
            <Route path="/home" element={<div>Pantalla de inicio</div>} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    );

    await screen.findByText('20:00');
    await userEvent.click(screen.getByRole('button', { name: /inicio/i }));
    expect(await screen.findByText('Pantalla de inicio')).toBeInTheDocument();
  });
});
