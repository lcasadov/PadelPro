import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { SplashPage } from '../pages/SplashPage';

function renderSplash() {
  return render(
    <AuthProvider>
      <MemoryRouter>
        <SplashPage />
      </MemoryRouter>
    </AuthProvider>
  );
}

describe('SplashPage', () => {
  it('renders the PadelPro brand', () => {
    renderSplash();
    expect(screen.getByText(/PadelPro/)).toBeDefined();
  });

  it('renders "Crear cuenta" link', () => {
    renderSplash();
    expect(screen.getByRole('link', { name: /Crear cuenta/i })).toBeDefined();
  });

  it('renders "Ya tengo cuenta" link', () => {
    renderSplash();
    expect(screen.getByRole('link', { name: /Ya tengo cuenta/i })).toBeDefined();
  });

  it('"Crear cuenta" links to /register', () => {
    renderSplash();
    const link = screen.getByRole('link', { name: /Crear cuenta/i });
    expect(link.getAttribute('href')).toBe('/register');
  });

  it('"Ya tengo cuenta" links to /login', () => {
    renderSplash();
    const link = screen.getByRole('link', { name: /Ya tengo cuenta/i });
    expect(link.getAttribute('href')).toBe('/login');
  });
});
