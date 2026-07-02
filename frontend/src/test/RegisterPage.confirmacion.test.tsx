// 7.3 — RegisterPage: pantalla de confirmación tras registro con acceso provisional.
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { RegisterPage } from '../pages/RegisterPage';
import { server } from './mocks/server';

function renderRegister() {
  return render(
    <MemoryRouter>
      <RegisterPage />
    </MemoryRouter>
  );
}

describe('RegisterPage confirmation (7.3)', () => {
  it('shows pending-approval and 2-day provisional access after a successful register', async () => {
    server.use(
      http.post('/api/auth/register', () =>
        HttpResponse.json({ id: 1, email: 'new@test.com', role: 'USER' }, { status: 201 })
      )
    );
    renderRegister();

    await userEvent.type(screen.getByLabelText(/Nombre/i), 'Ana');
    await userEvent.type(screen.getByLabelText(/Apellido/i), 'Ruiz');
    await userEvent.type(screen.getByLabelText(/Email/i), 'new@test.com');
    await userEvent.type(screen.getByLabelText(/Contraseña/i), 'Password1');
    await userEvent.click(screen.getByRole('button', { name: /Crear cuenta/i }));

    await waitFor(() => {
      expect(screen.getByText(/pendiente de aprobación/i)).toBeDefined();
    });
    // Mención del acceso provisional de 2 días
    expect(screen.getByText(/provisional|2 días|48/i)).toBeDefined();
  });
});
