// T-031 — LoginPage integration tests (Oleada 2 — RED phase)
// These tests MUST FAIL until Oleada 3 implements loginApi in authApi.ts.
// They describe the expected runtime behavior after the stubs are replaced.
//
// Specs covered:
//   R-2.1  — valid login → token stored in AuthContext + navigate to /home
//   R-2.2/R-2.3 — 401 → generic "Credenciales inválidas" (anti-enumeration)
//   R-2.4  — 403 → "Tu cuenta aún no está activada"
//   RN-AUTH-09 — token NEVER written to localStorage / sessionStorage

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';

import { AuthProvider, useAuth } from '../context/AuthContext';
import { LoginPage } from '../pages/LoginPage';
import { server } from './mocks/server';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Captures the AuthContext value so tests can assert on it after interaction.
 */
let capturedAccessToken: string | null = null;

function TokenSpy() {
  const { accessToken } = useAuth();
  capturedAccessToken = accessToken;
  return null;
}

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

function renderLoginWithAuth() {
  capturedAccessToken = null;
  return render(
    <AuthProvider>
      <MemoryRouter>
        <TokenSpy />
        <LoginPage />
      </MemoryRouter>
    </AuthProvider>
  );
}

async function fillAndSubmitLoginForm(email = 'player@test.com', password = 'Password1') {
  await userEvent.type(screen.getByLabelText(/Email/i), email);
  await userEvent.type(screen.getByLabelText(/Contraseña/i), password);
  await userEvent.click(screen.getByRole('button', { name: /Entrar/i }));
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('LoginPage integration (T-031) — RED: fails until Oleada 3', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('R-2.1 — calls loginApi and stores token in AuthContext on success, then navigates to /home', async () => {
    // Default MSW handler returns 200 with a JWT
    renderLoginWithAuth();

    await fillAndSubmitLoginForm('player@test.com', 'Password1');

    // Token must be set in context (loginApi resolved and setAccessToken called)
    await waitFor(() => {
      expect(capturedAccessToken).toBe('test.jwt.token');
    });

    // After successful login, the page should redirect to /home
    expect(mockNavigate).toHaveBeenCalledWith('/home');
  });

  it('R-2.2/R-2.3 — shows generic error "Credenciales inválidas" on 401 (anti-enumeration)', async () => {
    // Override: 401 AUTH_INVALID_CREDENTIALS
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ error: 'AUTH_INVALID_CREDENTIALS' }, { status: 401 })
      )
    );

    renderLoginWithAuth();
    await fillAndSubmitLoginForm('nobody@test.com', 'wrongPass');

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Credenciales inválidas');
    });

    // navigate must NOT be called on error
    expect(mockNavigate).not.toHaveBeenCalledWith('/home');
  });

  it('R-2.2/R-2.3 — same generic message for wrong password (anti-enumeration symmetry)', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ error: 'AUTH_INVALID_CREDENTIALS' }, { status: 401 })
      )
    );

    renderLoginWithAuth();
    await fillAndSubmitLoginForm('existing@test.com', 'wrongPassword');

    await waitFor(() => {
      const alert = screen.getByRole('alert');
      expect(alert).toHaveTextContent('Credenciales inválidas');
    });
  });

  it('R-2.4 — shows "Tu cuenta aún no está activada" on 403 ACCOUNT_NOT_ACTIVE', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ error: 'ACCOUNT_NOT_ACTIVE' }, { status: 403 })
      )
    );

    renderLoginWithAuth();
    await fillAndSubmitLoginForm('inactive@test.com', 'Password1');

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Tu cuenta aún no está activada'
      );
    });
  });

  it('RN-AUTH-09 — token is NOT stored in localStorage or sessionStorage after successful login', async () => {
    // Default handler returns 200 with JWT
    renderLoginWithAuth();
    await fillAndSubmitLoginForm('player@test.com', 'Password1');

    // Wait for any async effect
    await waitFor(() => {
      expect(capturedAccessToken).toBe('test.jwt.token');
    });

    // Storage must be empty
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
