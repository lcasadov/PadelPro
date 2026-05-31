// T-144 — MiPerfilPage integration tests (TDD — GREEN phase)
// Covers:
//   P-1 — render profile data loaded from GET /api/usuarios/me
//   P-2 — show success message after PATCH /api/usuarios/me → 200
//   P-3 — show email conflict error on PATCH → 409 USUARIOS_EMAIL_CONFLICT
//   P-4 — redirect to /login when not authenticated

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';

import { AuthContext } from '../context/AuthContext';
import { MiPerfilPage } from '../pages/MiPerfilPage';
import { server } from './mocks/server';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const mockProfile = {
  id: 42,
  login: 'lauracasado',
  firstName: 'Laura',
  lastName: 'Casado',
  email: 'laura.casado@email.com',
  phone: '+34612345678',
  status: 'ACTIVO',
  role: 'JUGADOR',
  telegramLinked: true,
};

// ---------------------------------------------------------------------------
// MSW handler helpers (called inside tests that need non-default behaviour)
// ---------------------------------------------------------------------------
function mockGetMe200() {
  server.use(
    http.get('/api/usuarios/me', () => HttpResponse.json(mockProfile, { status: 200 }))
  );
}

function mockPatchMe200() {
  server.use(
    http.patch('/api/usuarios/me', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      return HttpResponse.json({ ...mockProfile, ...body }, { status: 200 });
    })
  );
}

function mockPatchMe409() {
  server.use(
    http.patch('/api/usuarios/me', () =>
      HttpResponse.json(
        { code: 'USUARIOS_EMAIL_CONFLICT', message: 'El email ya está registrado' },
        { status: 409 }
      )
    )
  );
}

// ---------------------------------------------------------------------------
// Render helpers
// ---------------------------------------------------------------------------

/**
 * Renders MiPerfilPage inside a controlled AuthContext with an optional token.
 * Using a custom Provider value avoids the async-effect timing issue that arises
 * when setting the token via useEffect inside an AuthSeeder child component.
 */
function renderWithToken(token: string | null) {
  const ctxValue = {
    accessToken: token,
    setAccessToken: () => {},
    isAuthenticated: !!token,
  };

  return render(
    <AuthContext.Provider value={ctxValue}>
      <MemoryRouter initialEntries={['/perfil']}>
        <Routes>
          <Route path="/perfil" element={<MiPerfilPage />} />
          <Route path="/login" element={<div>Login Page</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('MiPerfilPage (T-144)', () => {
  beforeEach(() => {
    mockGetMe200();
    mockPatchMe200();
  });

  it('P-1 — should render profile data loaded from API', async () => {
    renderWithToken('test.jwt.token');

    await waitFor(() => {
      expect(screen.getByDisplayValue('Laura')).toBeInTheDocument();
    });

    expect(screen.getByDisplayValue('Casado')).toBeInTheDocument();
    expect(screen.getByDisplayValue('laura.casado@email.com')).toBeInTheDocument();
  });

  it('P-2 — should show success message after update', async () => {
    renderWithToken('test.jwt.token');

    await waitFor(() => {
      expect(screen.getByDisplayValue('Laura')).toBeInTheDocument();
    });

    // Change firstName
    const firstNameInput = screen.getByLabelText(/nombre/i);
    await userEvent.clear(firstNameInput);
    await userEvent.type(firstNameInput, 'Laura Updated');

    // Submit
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => {
      expect(screen.getByText(/perfil actualizado/i)).toBeInTheDocument();
    });
  });

  it('P-3 — should show email conflict error on 409', async () => {
    mockPatchMe409();
    renderWithToken('test.jwt.token');

    // Wait for form to load
    await waitFor(() => {
      expect(screen.getByDisplayValue('laura.casado@email.com')).toBeInTheDocument();
    });

    // Change email to something that conflicts
    const emailInput = screen.getByLabelText(/email/i);
    await userEvent.clear(emailInput);
    await userEvent.type(emailInput, 'existing@email.com');

    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('El email ya está registrado');
    });
  });

  it('P-4 — should redirect to /login if not authenticated', async () => {
    renderWithToken(null);

    // Synchronous redirect — no need to wait for async operations
    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });
});
