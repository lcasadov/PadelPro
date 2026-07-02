// 7.4 — ChangePasswordPage (TDD RED): cambio de contraseña forzado.
// Usa POST /api/usuarios/me/password. Al éxito limpia el flag y navega a /home.
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { ChangePasswordPage } from '../pages/ChangePasswordPage';
import { server } from './mocks/server';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

const clearFlag = vi.fn();

function renderPage(mustChange = true) {
  const value = {
    accessToken: 'tok',
    role: 'USER',
    mustChangePassword: mustChange,
    isAuthenticated: true,
    setAccessToken: () => {},
    setSession: () => {},
    clearMustChangePassword: clearFlag,
  };
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter>
        <ChangePasswordPage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

async function fill(current = 'Temp1234', next = 'NewPass123', confirm = 'NewPass123') {
  await userEvent.type(screen.getByLabelText(/contraseña actual/i), current);
  await userEvent.type(screen.getByLabelText(/^nueva contraseña/i), next);
  await userEvent.type(screen.getByLabelText(/confirm/i), confirm);
  await userEvent.click(screen.getByRole('button', { name: /cambiar|guardar/i }));
}

describe('ChangePasswordPage', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
    clearFlag.mockReset();
  });

  it('7.4 — on success clears the flag and navigates to /home', async () => {
    server.use(
      http.post('/api/usuarios/me/password', () => new HttpResponse(null, { status: 204 }))
    );
    renderPage();
    await fill();
    await waitFor(() => {
      expect(clearFlag).toHaveBeenCalled();
      expect(mockNavigate).toHaveBeenCalledWith('/home');
    });
  });

  it('shows an error when current password is wrong (401)', async () => {
    server.use(
      http.post('/api/usuarios/me/password', () =>
        HttpResponse.json({ error: 'AUTH_INVALID_CREDENTIALS' }, { status: 401 })
      )
    );
    renderPage();
    await fill('WrongCurrent', 'NewPass123', 'NewPass123');
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/actual.*incorrecta|incorrecta/i);
    });
    expect(mockNavigate).not.toHaveBeenCalledWith('/home');
  });

  it('shows a policy error on 400 INVALID_PASSWORD', async () => {
    server.use(
      http.post('/api/usuarios/me/password', () =>
        HttpResponse.json(
          { error: 'INVALID_PASSWORD', details: ['MIN_LENGTH_8'] },
          { status: 400 }
        )
      )
    );
    renderPage();
    await fill('Temp1234', 'short', 'short');
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/8 caracteres|no válida/i);
    });
  });

  it('validates that the confirmation matches before calling the API', async () => {
    renderPage();
    await fill('Temp1234', 'NewPass123', 'Mismatch999');
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/no coinciden/i);
    });
    expect(mockNavigate).not.toHaveBeenCalledWith('/home');
  });
});
