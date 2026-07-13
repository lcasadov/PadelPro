// admin-config-club (4.1) — ConfiguracionPage.
// Cubre: carga inicial, guardar precio, dejar secreto en blanco NO lo envía, teclear
// secreto SÍ lo envía, valor inválido no deja guardar, y estado de error.
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { ConfiguracionPage } from '../pages/ConfiguracionPage';
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

const config = {
  clubName: 'Club Padel Pro',
  clubDescription: 'El mejor club',
  pistaState: 'ACTIVA',
  paymentGateway: 'CASH',
  maxParticipantsPerPista: 4,
  pricePerHour: 10,
  cancellationDeadlineHours: 24,
  telegramBotConfigured: false,
  redsysConfigured: false,
  updatedAt: '2026-07-12T10:00:00Z',
};

function renderPage() {
  return render(
    <AuthContext.Provider value={adminValue}>
      <MemoryRouter>
        <ConfiguracionPage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

function stubGet(overrides: Partial<typeof config> = {}) {
  server.use(
    http.get('/api/admin/sistema/config', () => HttpResponse.json({ ...config, ...overrides }))
  );
}

describe('ConfiguracionPage', () => {
  it('carga la configuración actual', async () => {
    stubGet();
    renderPage();
    const price = (await screen.findByLabelText(/precio por hora/i)) as HTMLInputElement;
    expect(price.value).toBe('10');
    expect((screen.getByLabelText(/nombre del club/i) as HTMLInputElement).value).toBe('Club Padel Pro');
  });

  it('guarda el precio por hora vía PATCH', async () => {
    stubGet();
    let patched: Record<string, unknown> | null = null;
    server.use(
      http.patch('/api/admin/sistema/config', async ({ request }) => {
        patched = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...config, pricePerHour: 12.5 });
      })
    );
    renderPage();

    const price = await screen.findByLabelText(/precio por hora/i);
    await userEvent.clear(price);
    await userEvent.type(price, '12.5');
    await userEvent.click(screen.getByRole('button', { name: /guardar configuración/i }));

    await waitFor(() => expect(patched).not.toBeNull());
    expect(patched!.pricePerHour).toBe(12.5);
    expect(await screen.findByRole('status')).toHaveTextContent(/guardada/i);
  });

  it('dejar el bot token en blanco NO lo envía en el PATCH', async () => {
    stubGet({ telegramBotConfigured: true });
    let patched: Record<string, unknown> | null = null;
    server.use(
      http.patch('/api/admin/sistema/config', async ({ request }) => {
        patched = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(config);
      })
    );
    renderPage();

    await screen.findByLabelText(/precio por hora/i);
    await userEvent.click(screen.getByRole('button', { name: /guardar configuración/i }));

    await waitFor(() => expect(patched).not.toBeNull());
    expect('telegramBotToken' in patched!).toBe(false);
  });

  it('teclear un bot token SÍ lo envía en el PATCH', async () => {
    stubGet();
    let patched: Record<string, unknown> | null = null;
    server.use(
      http.patch('/api/admin/sistema/config', async ({ request }) => {
        patched = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...config, telegramBotConfigured: true });
      })
    );
    renderPage();

    const token = await screen.findByLabelText(/bot token/i);
    await userEvent.type(token, '123456:ABCDEF');
    await userEvent.click(screen.getByRole('button', { name: /guardar configuración/i }));

    await waitFor(() => expect(patched).not.toBeNull());
    expect(patched!.telegramBotToken).toBe('123456:ABCDEF');
  });

  it('un precio inválido no deja guardar y avisa', async () => {
    stubGet();
    let patchCalled = false;
    server.use(
      http.patch('/api/admin/sistema/config', () => {
        patchCalled = true;
        return HttpResponse.json(config);
      })
    );
    renderPage();

    const price = await screen.findByLabelText(/precio por hora/i);
    await userEvent.clear(price);
    await userEvent.type(price, '0');
    await userEvent.click(screen.getByRole('button', { name: /guardar configuración/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/precio por hora debe ser mayor/i);
    expect(patchCalled).toBe(false);
  });

  it('muestra error si la carga falla', async () => {
    server.use(
      http.get('/api/admin/sistema/config', () =>
        HttpResponse.json({ error: 'SERVER_ERROR' }, { status: 500 })
      )
    );
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudo cargar/i);
  });
});
