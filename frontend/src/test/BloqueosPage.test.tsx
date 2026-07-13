// bloqueos-pista-eventos (5.3) — BloqueosPage.
// Cubre: rejilla con estados LIBRE/OCUPADA/BLOQUEADA, seleccionar libres + motivo →
// POST, conflicto 409 avisa, y desbloquear → DELETE + refresco.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { BloqueosPage } from '../pages/BloqueosPage';
import { server } from './mocks/server';

afterEach(() => server.resetHandlers());

const adminValue = {
  accessToken: 'admin.jwt.token',
  role: 'ADMIN',
  mustChangePassword: false,
  userId: 1,
  isAuthenticated: true,
  setAccessToken: () => {},
  setSession: () => {},
  clearMustChangePassword: () => {},
  loadUserId: async () => {},
};

// 08:00 y 09:00 libres (creable); 10:00 ocupada (ausente de disponibles); 11:00 bloqueada.
const disponibilidad = {
  fecha: '2026-08-01',
  tramosDisponibles: [
    { horaInicio: '08:00', duracionMinutos: 60, plazasLibres: 4, creable: true },
    { horaInicio: '09:00', duracionMinutos: 60, plazasLibres: 4, creable: true },
    { horaInicio: '10:00', duracionMinutos: 60, plazasLibres: 2, creable: false },
  ],
};
const bloqueos = [{ id: 7, fecha: '2026-08-01', hora: '11:00', motivo: 'Torneo' }];

function stubLoad(bloqs = bloqueos) {
  server.use(
    http.get('/api/reservas/disponibles', () => HttpResponse.json(disponibilidad)),
    http.get('/api/admin/bloqueos', () => HttpResponse.json(bloqs))
  );
}

function renderPage() {
  return render(
    <AuthContext.Provider value={adminValue}>
      <MemoryRouter>
        <BloqueosPage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

function slotDe(hora: string): HTMLElement {
  return screen.getByText(hora).closest('li') as HTMLElement;
}

describe('BloqueosPage', () => {
  it('pinta la rejilla con los estados por franja', async () => {
    stubLoad();
    renderPage();
    await waitFor(() => expect(screen.getByText('08:00')).toBeInTheDocument());

    expect(within(slotDe('08:00')).getByRole('checkbox', { name: /bloquear 08:00/i })).toBeInTheDocument();
    expect(within(slotDe('10:00')).getByText(/reservada/i)).toBeInTheDocument();
    expect(within(slotDe('11:00')).getByText(/bloqueada/i)).toBeInTheDocument();
    expect(within(slotDe('11:00')).getByRole('button', { name: /desbloquear/i })).toBeInTheDocument();
  });

  it('bloquear franjas libres con motivo llama al POST', async () => {
    stubLoad();
    let posted: { fecha?: string; horas?: string[]; motivo?: string } | null = null;
    server.use(
      http.post('/api/admin/bloqueos', async ({ request }) => {
        posted = (await request.json()) as typeof posted;
        return HttpResponse.json([{ id: 9, fecha: '2026-08-01', hora: '08:00', motivo: 'Torneo' }]);
      })
    );
    renderPage();
    await waitFor(() => expect(screen.getByText('08:00')).toBeInTheDocument());

    await userEvent.click(within(slotDe('08:00')).getByRole('checkbox'));
    await userEvent.type(screen.getByLabelText(/motivo/i), 'Torneo');
    await userEvent.click(screen.getByRole('button', { name: /bloquear 1 franja/i }));

    await waitFor(() => expect(posted).not.toBeNull());
    expect(posted!.horas).toEqual(['08:00']);
    expect(posted!.motivo).toBe('Torneo');
    expect(await screen.findByRole('status')).toHaveTextContent(/bloqueadas/i);
  });

  it('avisa cuando el backend responde conflicto (409)', async () => {
    stubLoad();
    server.use(
      http.post('/api/admin/bloqueos', () =>
        HttpResponse.json(
          { error: 'CONFLICT', message: 'reserva activa', details: ['09:00'] },
          { status: 409 }
        )
      )
    );
    renderPage();
    await waitFor(() => expect(screen.getByText('09:00')).toBeInTheDocument());

    await userEvent.click(within(slotDe('09:00')).getByRole('checkbox'));
    await userEvent.type(screen.getByLabelText(/motivo/i), 'Torneo');
    await userEvent.click(screen.getByRole('button', { name: /bloquear 1 franja/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/reserva en alguna franja/i);
  });

  it('desbloquear llama al DELETE y refresca', async () => {
    stubLoad();
    let deleted = false;
    server.use(
      http.delete('/api/admin/bloqueos/7', () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
      http.get('/api/admin/bloqueos', () => HttpResponse.json(deleted ? [] : bloqueos))
    );
    renderPage();
    await waitFor(() => expect(screen.getByText('11:00')).toBeInTheDocument());

    await userEvent.click(within(slotDe('11:00')).getByRole('button', { name: /desbloquear/i }));

    await waitFor(() => expect(deleted).toBe(true));
    await waitFor(() =>
      expect(within(slotDe('11:00')).queryByRole('button', { name: /desbloquear/i })).toBeNull()
    );
  });

  it('exige motivo antes de bloquear', async () => {
    stubLoad();
    renderPage();
    await waitFor(() => expect(screen.getByText('08:00')).toBeInTheDocument());

    await userEvent.click(within(slotDe('08:00')).getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: /bloquear 1 franja/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/motivo/i);
  });
});
