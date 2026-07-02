// 6.2 — adminUsuariosApi (TDD RED) — validado contra MSW.
// Cubre: listar con filtro de estado, aprobar, actualizar estado, desactivar,
// reset-password (temporal devuelta una vez).
import { describe, it, expect, afterEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import {
  listUsuariosApi,
  aprobarUsuarioApi,
  updateUsuarioEstadoApi,
  desactivarUsuarioApi,
  resetPasswordApi,
} from '../services/adminUsuariosApi';

const TOKEN = 'admin.jwt.token';

afterEach(() => server.resetHandlers());

describe('adminUsuariosApi', () => {
  it('listUsuariosApi sends status/page/size and Authorization header', async () => {
    let capturedUrl = '';
    let capturedAuth = '';
    server.use(
      http.get('/api/admin/usuarios', ({ request }) => {
        capturedUrl = request.url;
        capturedAuth = request.headers.get('Authorization') ?? '';
        return HttpResponse.json({
          content: [
            { id: 1, email: 'a@test.com', firstName: 'Ana', lastName: 'Ruiz', status: 'PENDING', role: 'USER' },
          ],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        });
      })
    );

    const result = await listUsuariosApi(TOKEN, { status: 'PENDING', page: 0, size: 20 });

    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    expect(capturedUrl).toContain('status=PENDING');
    expect(capturedUrl).toContain('page=0');
    expect(capturedUrl).toContain('size=20');
    expect(result.content).toHaveLength(1);
    expect(result.content[0].status).toBe('PENDING');
  });

  it('aprobarUsuarioApi calls PATCH .../aprobar', async () => {
    let hit = false;
    server.use(
      http.patch('/api/admin/usuarios/7/aprobar', () => {
        hit = true;
        return HttpResponse.json({ id: 7, email: 'x@test.com', firstName: 'X', lastName: 'Y', status: 'ACTIVE', role: 'USER' });
      })
    );
    const updated = await aprobarUsuarioApi(TOKEN, 7);
    expect(hit).toBe(true);
    expect(updated.status).toBe('ACTIVE');
  });

  it('updateUsuarioEstadoApi PATCHes the status field', async () => {
    let body: unknown = null;
    server.use(
      http.patch('/api/admin/usuarios/7', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 7, email: 'x@test.com', firstName: 'X', lastName: 'Y', status: 'INACTIVE', role: 'USER' });
      })
    );
    const updated = await updateUsuarioEstadoApi(TOKEN, 7, 'INACTIVE');
    expect(body).toMatchObject({ status: 'INACTIVE' });
    expect(updated.status).toBe('INACTIVE');
  });

  it('desactivarUsuarioApi calls DELETE', async () => {
    let hit = false;
    server.use(
      http.delete('/api/admin/usuarios/7', () => {
        hit = true;
        return new HttpResponse(null, { status: 204 });
      })
    );
    await desactivarUsuarioApi(TOKEN, 7);
    expect(hit).toBe(true);
  });

  it('resetPasswordApi returns the temporary password once', async () => {
    server.use(
      http.patch('/api/admin/usuarios/7/reset-password', () =>
        HttpResponse.json({ user_id: 7, temporary_password: 'Temp1234abc', must_change_password: true })
      )
    );
    const result = await resetPasswordApi(TOKEN, 7);
    expect(result.temporary_password).toBe('Temp1234abc');
    expect(result.must_change_password).toBe(true);
  });
});
