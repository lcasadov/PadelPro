import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
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

  it('stub submit logs to console', () => {
    const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    renderLogin();

    fireEvent.change(screen.getByLabelText(/Email/i), {
      target: { value: 'test@test.com' },
    });
    fireEvent.change(screen.getByLabelText(/Contraseña/i), {
      target: { value: 'secret' },
    });
    fireEvent.submit(screen.getByRole('button', { name: /Entrar/i }).closest('form')!);

    expect(consoleSpy).toHaveBeenCalledWith(
      'login stub',
      { email: 'test@test.com', password: 'secret' }
    );
    consoleSpy.mockRestore();
  });
});
