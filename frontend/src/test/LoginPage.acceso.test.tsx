// 7.1 / 7.2 / 7.4 — LoginPage UX de acceso (TDD RED)
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthProvider } from '../context/AuthContext';
import { LoginPage } from '../pages/LoginPage';
import { server } from './mocks/server';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

function renderLogin() {
  return render(
    <AuthProvider>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </AuthProvider>
  );
}

async function submit(email = 'user@test.com', password = 'Password1') {
  await userEvent.type(screen.getByLabelText(/Email/i), email);
  await userEvent.type(screen.getByLabelText(/Contraseña/i), password);
  await userEvent.click(screen.getByRole('button', { name: /Entrar/i }));
}

describe('LoginPage — UX de acceso (acceso-cuenta-prod)', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
  });

  it('7.1 — "¿Olvidaste la contraseña?" navigates to /forgot-password', () => {
    renderLogin();
    const link = screen.getByRole('link', { name: /olvidaste la contraseña/i });
    expect(link.getAttribute('href')).toBe('/forgot-password');
  });

  it('7.2 — maps 403 ACCOUNT_NOT_ACTIVE to a pending/blocked message', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ error: 'ACCOUNT_NOT_ACTIVE' }, { status: 403 })
      )
    );
    renderLogin();
    await submit('pending@test.com', 'Password1');
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/no está activada|pendiente/i);
    });
    expect(mockNavigate).not.toHaveBeenCalledWith('/home');
  });

  it('7.2 — maps 401 to invalid credentials', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ error: 'AUTH_INVALID_CREDENTIALS' }, { status: 401 })
      )
    );
    renderLogin();
    await submit('x@test.com', 'bad');
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/credenciales inválidas/i);
    });
  });

  it('7.4 — redirects to forced password change when must_change_password is true', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(
          {
            access_token: 'test.jwt.token',
            token_type: 'Bearer',
            expires_in: 900,
            role: 'USER',
            must_change_password: true,
          },
          { status: 200 }
        )
      )
    );
    renderLogin();
    await submit('reset@test.com', 'Temp1234');
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/cambiar-password');
    });
    expect(mockNavigate).not.toHaveBeenCalledWith('/home');
  });

  it('7.4 — navigates to /home when must_change_password is false', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(
          {
            access_token: 'test.jwt.token',
            token_type: 'Bearer',
            expires_in: 900,
            role: 'USER',
            must_change_password: false,
          },
          { status: 200 }
        )
      )
    );
    renderLogin();
    await submit('ok@test.com', 'Password1');
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/home');
    });
  });
});
