// T-032 — RegisterPage integration tests (Oleada 2 — RED phase)
// These tests MUST FAIL until Oleada 3 implements registerApi in authApi.ts.
// They describe the expected runtime behavior after the stubs are replaced.
//
// Specs covered:
//   R-1.1 — valid registration → 201 → confirmation screen visible
//   R-1.2 — 409 → "Este email ya está registrado"
//   R-1.3a — 400 INVALID_PASSWORD / MIN_LENGTH_8 → password-too-short message
//   R-1.3b — 400 INVALID_PASSWORD / REQUIRES_UPPERCASE → missing uppercase message
//   R-1.3c — 400 INVALID_PASSWORD / REQUIRES_NUMBER → missing number message
//   R-1.4 — required field empty → validation prevents API call

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';

import { AuthProvider } from '../context/AuthContext';
import { RegisterPage } from '../pages/RegisterPage';
import * as authApi from '../services/authApi';
import { server } from './mocks/server';

// ---------------------------------------------------------------------------
// Mock react-router navigate
// ---------------------------------------------------------------------------
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function renderRegisterWithAuth() {
  return render(
    <AuthProvider>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </AuthProvider>
  );
}

async function fillAndSubmitRegisterForm(opts?: {
  firstName?: string;
  lastName?: string;
  email?: string;
  password?: string;
}) {
  const {
    firstName = 'Laura',
    lastName = 'Casado',
    email = 'laura@test.com',
    password = 'Password1',
  } = opts ?? {};

  if (firstName) await userEvent.type(screen.getByLabelText(/Nombre/i), firstName);
  if (lastName) await userEvent.type(screen.getByLabelText(/Apellido/i), lastName);
  if (email) await userEvent.type(screen.getByLabelText(/Email/i), email);
  if (password) await userEvent.type(screen.getByLabelText(/Contraseña/i), password);

  await userEvent.click(screen.getByRole('button', { name: /Crear cuenta/i }));
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('RegisterPage integration (T-032) — RED: fails until Oleada 3', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
  });

  it('R-1.1 — calls registerApi and shows confirmation screen on 201', async () => {
    // Default MSW handler returns 201 { id: 1, email: 'user@test.com', role: 'USER' }
    const registerSpy = vi.spyOn(authApi, 'registerApi');

    renderRegisterWithAuth();
    await fillAndSubmitRegisterForm();

    // registerApi must have been called with the correct payload
    await waitFor(() => {
      expect(registerSpy).toHaveBeenCalledWith({
        firstName: 'Laura',
        lastName: 'Casado',
        email: 'laura@test.com',
        password: 'Password1',
      });
    });

    // After success, a confirmation screen must be visible
    await waitFor(() => {
      expect(
        screen.getByText(/cuenta creada|confirma|revisa tu email|verificación/i)
      ).toBeInTheDocument();
    });
  });

  it('R-1.2 — shows "Este email ya está registrado" on 409', async () => {
    server.use(
      http.post('/api/auth/register', () =>
        HttpResponse.json({ error: 'EMAIL_ALREADY_REGISTERED' }, { status: 409 })
      )
    );

    renderRegisterWithAuth();
    await fillAndSubmitRegisterForm({ email: 'duplicate@test.com' });

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Este email ya está registrado'
      );
    });
  });

  it('R-1.3a — shows password-too-short message on 400 MIN_LENGTH_8', async () => {
    server.use(
      http.post('/api/auth/register', () =>
        HttpResponse.json(
          { error: 'INVALID_PASSWORD', details: ['MIN_LENGTH_8'] },
          { status: 400 }
        )
      )
    );

    renderRegisterWithAuth();
    await fillAndSubmitRegisterForm({ password: 'Short1' });

    await waitFor(() => {
      // The UI must surface a human-readable message for MIN_LENGTH_8
      expect(screen.getByRole('alert')).toHaveTextContent(
        /mínimo 8 caracteres|al menos 8/i
      );
    });
  });

  it('R-1.3b — shows missing uppercase message on 400 REQUIRES_UPPERCASE', async () => {
    server.use(
      http.post('/api/auth/register', () =>
        HttpResponse.json(
          { error: 'INVALID_PASSWORD', details: ['REQUIRES_UPPERCASE'] },
          { status: 400 }
        )
      )
    );

    renderRegisterWithAuth();
    await fillAndSubmitRegisterForm({ password: 'password1' });

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        /mayúscula|uppercase/i
      );
    });
  });

  it('R-1.3c — shows missing number message on 400 REQUIRES_NUMBER', async () => {
    server.use(
      http.post('/api/auth/register', () =>
        HttpResponse.json(
          { error: 'INVALID_PASSWORD', details: ['REQUIRES_NUMBER'] },
          { status: 400 }
        )
      )
    );

    renderRegisterWithAuth();
    await fillAndSubmitRegisterForm({ password: 'Password' });

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        /número|dígito|number/i
      );
    });
  });

  it('R-1.4 — does NOT call registerApi when required fields are empty', async () => {
    const registerSpy = vi.spyOn(authApi, 'registerApi');

    renderRegisterWithAuth();

    // Submit with all fields empty (the form has noValidate but component should guard)
    await userEvent.click(screen.getByRole('button', { name: /Crear cuenta/i }));

    // Give any async handlers a chance to run
    await waitFor(() => {
      expect(registerSpy).not.toHaveBeenCalled();
    });
  });
});
