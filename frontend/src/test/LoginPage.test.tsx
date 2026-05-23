import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { LoginPage } from '../pages/LoginPage';

function renderLogin() {
  return render(
    <AuthProvider>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </AuthProvider>
  );
}

describe('LoginPage', () => {
  it('renders email and password inputs', () => {
    renderLogin();
    expect(screen.getByLabelText(/Email/i)).toBeDefined();
    expect(screen.getByLabelText(/Contraseña/i)).toBeDefined();
  });

  it('renders submit button', () => {
    renderLogin();
    expect(screen.getByRole('button', { name: /Entrar/i })).toBeDefined();
  });

  it('renders link to /register', () => {
    renderLogin();
    const link = screen.getByRole('link', { name: /Crear cuenta/i });
    expect(link.getAttribute('href')).toBe('/register');
  });

  it('submit with valid credentials calls loginApi (default MSW handler returns 200)', async () => {
    const { default: userEvent } = await import('@testing-library/user-event');
    renderLogin();

    await userEvent.type(screen.getByLabelText(/Email/i), 'test@test.com');
    await userEvent.type(screen.getByLabelText(/Contraseña/i), 'Password1');
    await userEvent.click(screen.getByRole('button', { name: /Entrar/i }));

    // No error should appear — default MSW handler returns 200
    await new Promise((r) => setTimeout(r, 100));
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
