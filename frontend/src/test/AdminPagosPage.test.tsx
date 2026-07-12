// pagos-simulador-gestion (4.2) — AdminPagosPage.
// Cubre: lista de pagos, filtro Todas/Pagadas/Pendientes, "marcar como pagada
// (efectivo)" refresca, y estado de error.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { AdminPagosPage } from '../pages/AdminPagosPage';
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

function renderPage() {
  return render(
    <AuthContext.Provider value={adminValue}>
      <MemoryRouter>
        <AdminPagosPage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

const pagos = [
  {
    id: 'p-1',
    reservaId: 'r-1',
    amount: 16,
    status: 'PAID',
    titular: 'Ana Ruiz',
    paidAt: '2026-07-10T12:00:00Z',
    createdAt: '2026-07-09T10:00:00Z',
  },
  {
    id: 'p-2',
    reservaId: 'r-2',
    amount: 24,
    status: 'PENDING',
    titular: 'Leo Paz',
    createdAt: '2026-07-11T09:00:00Z',
  },
];

describe('AdminPagosPage', () => {
  beforeEach(() => {
    server.use(http.get('/api/admin/pagos', () => HttpResponse.json(pagos)));
  });

  it('lista todos los pagos con titular e importe', async () => {
    renderPage();
    expect(await screen.findByText('Ana Ruiz')).toBeInTheDocument();
    expect(screen.getByText('Leo Paz')).toBeInTheDocument();
    expect(screen.getByText('16,00 €')).toBeInTheDocument();
    expect(screen.getByText('24,00 €')).toBeInTheDocument();
  });

  it('filtra Pagadas (oculta las pendientes)', async () => {
    renderPage();
    await screen.findByText('Ana Ruiz');

    await userEvent.click(screen.getByRole('button', { name: 'Pagadas' }));

    await waitFor(() => {
      expect(screen.getByText('Ana Ruiz')).toBeInTheDocument();
      expect(screen.queryByText('Leo Paz')).toBeNull();
    });
  });

  it('filtra Pendientes (oculta las pagadas)', async () => {
    renderPage();
    await screen.findByText('Leo Paz');

    await userEvent.click(screen.getByRole('button', { name: 'Pendientes' }));

    await waitFor(() => {
      expect(screen.getByText('Leo Paz')).toBeInTheDocument();
      expect(screen.queryByText('Ana Ruiz')).toBeNull();
    });
  });

  it('solo las pendientes ofrecen "marcar como pagada (efectivo)"', async () => {
    renderPage();
    const filaPagada = (await screen.findByText('Ana Ruiz')).closest('tr') as HTMLElement;
    const filaPendiente = screen.getByText('Leo Paz').closest('tr') as HTMLElement;

    expect(within(filaPagada).queryByRole('button', { name: /marcar como pagada/i })).toBeNull();
    expect(within(filaPendiente).getByRole('button', { name: /marcar como pagada/i })).toBeInTheDocument();
  });

  it('marcar como pagada llama al endpoint y refresca la lista', async () => {
    let marcada = false;
    server.use(
      http.post('/api/admin/pagos/r-2/efectivo', () => {
        marcada = true;
        return new HttpResponse(null, { status: 200 });
      }),
      http.get('/api/admin/pagos', () =>
        HttpResponse.json(
          marcada
            ? [pagos[0], { ...pagos[1], status: 'PAID', paidAt: '2026-07-11T10:00:00Z' }]
            : pagos
        )
      )
    );
    renderPage();

    const fila = (await screen.findByText('Leo Paz')).closest('tr') as HTMLElement;
    await userEvent.click(within(fila).getByRole('button', { name: /marcar como pagada/i }));

    await waitFor(() => expect(marcada).toBe(true));
    // Tras refrescar, Leo Paz ya no tiene el botón (ahora PAID).
    await waitFor(() => {
      const filaRefrescada = screen.getByText('Leo Paz').closest('tr') as HTMLElement;
      expect(within(filaRefrescada).queryByRole('button', { name: /marcar como pagada/i })).toBeNull();
    });
  });

  it('muestra error si marcar como pagada falla', async () => {
    server.use(
      http.post('/api/admin/pagos/r-2/efectivo', () =>
        HttpResponse.json({ error: 'CONFLICT', message: 'ya pagada' }, { status: 409 })
      )
    );
    renderPage();

    const fila = (await screen.findByText('Leo Paz')).closest('tr') as HTMLElement;
    await userEvent.click(within(fila).getByRole('button', { name: /marcar como pagada/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudo marcar/i);
  });

  it('muestra error si la carga falla', async () => {
    server.use(
      http.get('/api/admin/pagos', () => HttpResponse.json({ error: 'SERVER_ERROR' }, { status: 500 }))
    );
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudo cargar/i);
  });
});
