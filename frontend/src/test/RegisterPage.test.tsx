import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
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

  it('stub submit logs to console', () => {
    const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    renderRegister();

    fireEvent.change(screen.getByLabelText(/Nombre/i), { target: { value: 'Laura' } });
    fireEvent.change(screen.getByLabelText(/Apellido/i), { target: { value: 'Casado' } });
    fireEvent.change(screen.getByLabelText(/Email/i), { target: { value: 'laura@test.com' } });
    fireEvent.change(screen.getByLabelText(/Contraseña/i), { target: { value: 'Abc12345' } });

    fireEvent.submit(
      screen.getByRole('button', { name: /Crear cuenta/i }).closest('form')!
    );

    expect(consoleSpy).toHaveBeenCalledWith(
      'register stub',
      { firstName: 'Laura', lastName: 'Casado', email: 'laura@test.com', password: 'Abc12345' }
    );
    consoleSpy.mockRestore();
  });
});
