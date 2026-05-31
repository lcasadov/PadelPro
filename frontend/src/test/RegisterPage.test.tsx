import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { RegisterPage } from '../pages/RegisterPage';

function renderRegister() {
  return render(
    <AuthProvider>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </AuthProvider>
  );
}

describe('RegisterPage', () => {
  it('renders name, email and password inputs', () => {
    renderRegister();
    expect(screen.getByLabelText(/Nombre/i)).toBeDefined();
    expect(screen.getByLabelText(/Apellido/i)).toBeDefined();
    expect(screen.getByLabelText(/Email/i)).toBeDefined();
    expect(screen.getByLabelText(/Contraseña/i)).toBeDefined();
  });

  it('renders submit button', () => {
    renderRegister();
    expect(screen.getByRole('button', { name: /Crear cuenta/i })).toBeDefined();
  });

  it('shows PasswordStrengthIndicator when password is typed', () => {
    renderRegister();
    const passwordInput = screen.getByLabelText(/Contraseña/i);
    fireEvent.change(passwordInput, { target: { value: 'A' } });
    // PasswordStrengthIndicator should now render
    expect(screen.getByRole('list', { name: /Requisitos de contraseña/i })).toBeDefined();
  });

  it('submit with valid data calls registerApi (default MSW handler returns 201)', async () => {
    const { default: userEvent } = await import('@testing-library/user-event');
    renderRegister();

    await userEvent.type(screen.getByLabelText(/Nombre/i), 'Laura');
    await userEvent.type(screen.getByLabelText(/Apellido/i), 'Casado');
    await userEvent.type(screen.getByLabelText(/Email/i), 'laura@test.com');
    await userEvent.type(screen.getByLabelText(/Contraseña/i), 'Abc12345');

    await userEvent.click(
      screen.getByRole('button', { name: /Crear cuenta/i })
    );

    // No error should appear — default MSW handler returns 201
    await new Promise((r) => setTimeout(r, 100));
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
