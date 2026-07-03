// 3.2/3.3 + 4.2/4.3 — AdminUsuariosPage alta y edición (TDD RED)
// 3.2: botón "Dar de alta" abre formulario (nombre, email, rol; SIN contraseña);
//      enviar crea y refresca la lista.
// 3.3: alta con email duplicado (409) muestra el error de conflicto.
// 4.2: acción "Editar" precarga datos; el formulario NO ofrece cambiar rol;
//      guardar aplica el PATCH y refresca la lista.
// 4.3: edición con datos inválidos (400) muestra el error.
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

let usuarios: Array<Record<string, unknown>>;

function pagedResponse(list: typeof usuarios) {
  return { content: list, totalElements: list.length, totalPages: 1, number: 0, size: 20 };
}

beforeEach(() => {
  usuarios = [
    { id: 1, email: 'ana@test.com', firstName: 'Ana', lastName: 'Ruiz', status: 'PENDING', role: 'USER', phone: '600111222' },
    { id: 2, email: 'leo@test.com', firstName: 'Leo', lastName: 'Paz', status: 'ACTIVE', role: 'USER', phone: '600333444' },
  ];
  server.use(
    http.get('/api/admin/usuarios', () => HttpResponse.json(pagedResponse(usuarios)))
  );
});

describe('AdminUsuariosPage — alta de usuario (3.2/3.3)', () => {
  it('3.2 — opens the alta form without a password field and creates + refreshes', async () => {
    let posted: Record<string, unknown> | null = null;
    server.use(
      http.post('/api/admin/usuarios', async ({ request }) => {
        posted = (await request.json()) as Record<string, unknown>;
        const nuevo = {
          id: 3,
          email: 'nuevo@test.com',
          firstName: 'Nuevo',
          lastName: 'Socio',
          status: 'ACTIVE',
          role: 'USER',
          phone: '',
        };
        usuarios = [...usuarios, nuevo];
        return HttpResponse.json(nuevo, { status: 201 });
      })
    );

    renderPage();
    await screen.findByText('ana@test.com');

    await userEvent.click(screen.getByRole('button', { name: /dar de alta/i }));

    const dialog = await screen.findByRole('dialog', { name: /dar de alta|alta de usuario/i });

    // No password field in the alta form (D2/D3).
    expect(within(dialog).queryByLabelText(/contrase/i)).toBeNull();

    await userEvent.type(within(dialog).getByLabelText(/nombre/i), 'Nuevo');
    await userEvent.type(within(dialog).getByLabelText(/apellidos?/i), 'Socio');
    await userEvent.type(within(dialog).getByLabelText(/email/i), 'nuevo@test.com');
    await userEvent.selectOptions(within(dialog).getByLabelText(/rol/i), 'USER');

    await userEvent.click(within(dialog).getByRole('button', { name: /crear|guardar|dar de alta/i }));

    await waitFor(() => expect(posted).not.toBeNull());
    expect(posted).not.toHaveProperty('password');
    expect(posted).toMatchObject({ email: 'nuevo@test.com', firstName: 'Nuevo', role: 'USER' });

    // List refreshed — the new user appears.
    expect(await screen.findByText('nuevo@test.com')).toBeDefined();
  });

  it('3.3 — shows a conflict error when the email is duplicated (409)', async () => {
    server.use(
      http.post('/api/admin/usuarios', () =>
        HttpResponse.json(
          { error: 'USUARIOS_EMAIL_CONFLICT', message: 'El email ya está registrado' },
          { status: 409 }
        )
      )
    );

    renderPage();
    await screen.findByText('ana@test.com');
    await userEvent.click(screen.getByRole('button', { name: /dar de alta/i }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/nombre/i), 'Dup');
    await userEvent.type(within(dialog).getByLabelText(/apellidos?/i), 'Licado');
    await userEvent.type(within(dialog).getByLabelText(/email/i), 'ana@test.com');
    await userEvent.click(within(dialog).getByRole('button', { name: /crear|guardar|dar de alta/i }));

    expect(await screen.findByText(/ya est|duplicad|registrad|conflicto/i)).toBeDefined();
  });
});

describe('AdminUsuariosPage — edición de usuario (4.2/4.3)', () => {
  it('4.2 — edit preloads data, offers no role field, and PATCHes + refreshes', async () => {
    let patched: Record<string, unknown> | null = null;
    server.use(
      http.patch('/api/admin/usuarios/2', async ({ request }) => {
        patched = (await request.json()) as Record<string, unknown>;
        usuarios = usuarios.map((u) =>
          u.id === 2 ? { ...u, firstName: 'LeoEdit', email: 'leo2@test.com' } : u
        );
        return HttpResponse.json({ id: 2, firstName: 'LeoEdit', lastName: 'Paz', email: 'leo2@test.com', phone: '600333444', status: 'ACTIVE', role: 'USER' });
      })
    );

    renderPage();
    const row = (await screen.findByText('leo@test.com')).closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: /editar/i }));

    const dialog = await screen.findByRole('dialog', { name: /editar/i });

    // Preloaded values.
    expect((within(dialog).getByLabelText(/nombre/i) as HTMLInputElement).value).toBe('Leo');
    expect((within(dialog).getByLabelText(/email/i) as HTMLInputElement).value).toBe('leo@test.com');

    // No role control in the edit form (D7).
    expect(within(dialog).queryByLabelText(/rol/i)).toBeNull();

    const nombre = within(dialog).getByLabelText(/nombre/i);
    await userEvent.clear(nombre);
    await userEvent.type(nombre, 'LeoEdit');
    const email = within(dialog).getByLabelText(/email/i);
    await userEvent.clear(email);
    await userEvent.type(email, 'leo2@test.com');

    await userEvent.click(within(dialog).getByRole('button', { name: /guardar|actualizar/i }));

    await waitFor(() => expect(patched).not.toBeNull());
    expect(patched).not.toHaveProperty('role');
    expect(patched).toMatchObject({ firstName: 'LeoEdit', email: 'leo2@test.com' });

    expect(await screen.findByText('leo2@test.com')).toBeDefined();
  });

  it('4.3 — shows a validation error when data is invalid (400)', async () => {
    server.use(
      http.patch('/api/admin/usuarios/2', () =>
        HttpResponse.json(
          { error: 'VALIDATION_ERROR', message: 'Email con formato inválido' },
          { status: 400 }
        )
      )
    );

    renderPage();
    const row = (await screen.findByText('leo@test.com')).closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: /editar/i }));
    const dialog = await screen.findByRole('dialog', { name: /editar/i });

    const email = within(dialog).getByLabelText(/email/i);
    await userEvent.clear(email);
    await userEvent.type(email, 'no-es-email');
    await userEvent.click(within(dialog).getByRole('button', { name: /guardar|actualizar/i }));

    expect(await screen.findByText(/inv|formato|válid/i)).toBeDefined();
  });
});
