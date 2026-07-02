// 6.3 + 6.4 — AdminUsuariosPage (TDD RED)
// 6.3: lista, filtra por estado (PENDING) y dispara aprobar.
// 6.4: la acción de reset muestra la temporal devuelta UNA sola vez con aviso.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { AdminUsuariosPage } from '../pages/AdminUsuariosPage';
import { server } from './mocks/server';

const adminValue = {
  accessToken: 'admin.jwt.token',
  role: 'ADMIN',
  mustChangePassword: false,
  isAuthenticated: true,
  setAccessToken: () => {},
  setSession: () => {},
  clearMustChangePassword: () => {},
};

function renderPage() {
  return render(
    <AuthContext.Provider value={adminValue}>
      <MemoryRouter>
        <AdminUsuariosPage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

const usuarios = [
  { id: 1, email: 'ana@test.com', firstName: 'Ana', lastName: 'Ruiz', status: 'PENDING', role: 'USER' },
  { id: 2, email: 'leo@test.com', firstName: 'Leo', lastName: 'Paz', status: 'ACTIVE', role: 'USER' },
];

function pagedResponse(list: typeof usuarios) {
  return {
    content: list,
    totalElements: list.length,
    totalPages: 1,
    number: 0,
    size: 20,
  };
}

describe('AdminUsuariosPage', () => {
  beforeEach(() => {
    server.use(
      http.get('/api/admin/usuarios', ({ request }) => {
        const status = new URL(request.url).searchParams.get('status');
        const filtered = status ? usuarios.filter((u) => u.status === status) : usuarios;
        return HttpResponse.json(pagedResponse(filtered));
      })
    );
  });

  it('6.3 — lists users on load', async () => {
    renderPage();
    expect(await screen.findByText('ana@test.com')).toBeDefined();
    expect(screen.getByText('leo@test.com')).toBeDefined();
  });

  it('6.3 — filters by status PENDING', async () => {
    renderPage();
    await screen.findByText('ana@test.com');

    await userEvent.selectOptions(screen.getByLabelText(/estado/i), 'PENDING');

    await waitFor(() => {
      expect(screen.getByText('ana@test.com')).toBeDefined();
      expect(screen.queryByText('leo@test.com')).toBeNull();
    });
  });

  it('6.3 — approves a PENDING user', async () => {
    let approved = false;
    server.use(
      http.patch('/api/admin/usuarios/1/aprobar', () => {
        approved = true;
        return HttpResponse.json({ ...usuarios[0], status: 'ACTIVE' });
      })
    );
    renderPage();
    const row = (await screen.findByText('ana@test.com')).closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: /aprobar/i }));
    await waitFor(() => expect(approved).toBe(true));
  });

  it('6.4 — reset shows the temporary password exactly once with a warning', async () => {
    server.use(
      http.patch('/api/admin/usuarios/2/reset-password', () =>
        HttpResponse.json({ user_id: 2, temporary_password: 'Temp1234abc', must_change_password: true })
      )
    );
    renderPage();
    const row = (await screen.findByText('leo@test.com')).closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: /restablecer|reset/i }));

    // The temporary password appears
    expect(await screen.findByText('Temp1234abc')).toBeDefined();
    // With a warning to communicate it and that the user must change it
    expect(screen.getByText(/una sola vez|comun|cambi/i)).toBeDefined();
  });
});
