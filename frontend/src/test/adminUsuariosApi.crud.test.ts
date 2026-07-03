// 3.1 + 4.1 — adminUsuariosApi alta/edición (TDD RED) — validado contra MSW.
// Cubre: crear usuario (POST, sin contraseña de entrada, 409 en conflicto de email)
// y editar usuario (PATCH datos de contacto sin rol, 400 en datos inválidos).
import { describe, it, expect, afterEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import { crearUsuarioApi, editarUsuarioApi } from '../services/adminUsuariosApi';

const TOKEN = 'admin.jwt.token';

afterEach(() => server.resetHandlers());

describe('crearUsuarioApi', () => {
  it('POSTs /api/admin/usuarios with name/email/role and no password field', async () => {
    let body: Record<string, unknown> = {};
    let capturedAuth = '';
    server.use(
      http.post('/api/admin/usuarios', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        capturedAuth = request.headers.get('Authorization') ?? '';
        return HttpResponse.json(
          {
            id: 10,
            login: 'nuevo@test.com',
            firstName: 'Nuevo',
            lastName: 'Socio',
            email: 'nuevo@test.com',
            phone: '600111222',
            status: 'ACTIVE',
            role: 'USER',
          },
          { status: 201 }
        );
      })
    );

    const created = await crearUsuarioApi(TOKEN, {
      firstName: 'Nuevo',
      lastName: 'Socio',
      email: 'nuevo@test.com',
      phone: '600111222',
      role: 'USER',
    });

    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    // The admin never types a password — the system generates it (D2/D3).
    expect(body).not.toHaveProperty('password');
    expect(body).toMatchObject({
      firstName: 'Nuevo',
      lastName: 'Socio',
      email: 'nuevo@test.com',
      role: 'USER',
    });
    expect(created.id).toBe(10);
    expect(created.status).toBe('ACTIVE');
  });

  it('derives login from email when creating', async () => {
    let body: Record<string, unknown> = {};
    server.use(
      http.post('/api/admin/usuarios', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 11, email: 'x@test.com', firstName: 'X', lastName: 'Y', status: 'ACTIVE', role: 'USER' }, { status: 201 });
      })
    );
    await crearUsuarioApi(TOKEN, { firstName: 'X', lastName: 'Y', email: 'x@test.com', role: 'USER' });
    expect(body.login).toBe('x@test.com');
  });

  it('propagates a 409 email conflict error', async () => {
    server.use(
      http.post('/api/admin/usuarios', () =>
        HttpResponse.json(
          { error: 'USUARIOS_EMAIL_CONFLICT', message: 'El email ya está registrado' },
          { status: 409 }
        )
      )
    );

    await expect(
      crearUsuarioApi(TOKEN, { firstName: 'Dup', lastName: 'Licado', email: 'dup@test.com', role: 'USER' })
    ).rejects.toMatchObject({ response: { status: 409 } });
  });
});

describe('editarUsuarioApi', () => {
  it('PATCHes /api/admin/usuarios/{id} with contact data and no role', async () => {
    let body: Record<string, unknown> = {};
    server.use(
      http.patch('/api/admin/usuarios/5', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({
          id: 5,
          firstName: 'Editado',
          lastName: 'Nombre',
          email: 'edit@test.com',
          phone: '699888777',
          status: 'ACTIVE',
          role: 'USER',
        });
      })
    );

    const updated = await editarUsuarioApi(TOKEN, 5, {
      firstName: 'Editado',
      lastName: 'Nombre',
      email: 'edit@test.com',
      phone: '699888777',
    });

    // Role must NOT be part of the edit payload (D7 — avoid privilege escalation).
    expect(body).not.toHaveProperty('role');
    expect(body).not.toHaveProperty('status');
    expect(body).toMatchObject({
      firstName: 'Editado',
      lastName: 'Nombre',
      email: 'edit@test.com',
      phone: '699888777',
    });
    expect(updated.firstName).toBe('Editado');
  });

  // NOTA (#179): el antiguo test "propagates a 400 validation error" era circular —
  // mockeaba el 400 con MSW y solo comprobaba que axios lo propagaba, sin ejercer
  // el contrato real de validación de email. Se ha eliminado y sustituido por:
  //  - Frontend: src/test/UsuarioFormModal.test.tsx (el componente valida el email
  //    en cliente y NO dispara la request cuando está mal formado).
  //  - Backend: AdminUsuariosControllerIntegrationTest (email mal formado → 400
  //    VALIDATION_ERROR real contra Postgres, no mockeado).
});
