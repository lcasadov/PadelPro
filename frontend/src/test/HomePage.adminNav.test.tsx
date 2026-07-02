// 6.5 — La entrada de navegación al panel admin solo se muestra a ADMIN.
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { HomePage } from '../pages/HomePage';

function renderHome(role: string | null) {
  const value = {
    accessToken: 'tok',
    role,
    mustChangePassword: false,
    isAuthenticated: true,
    setAccessToken: () => {},
    setSession: () => {},
    clearMustChangePassword: () => {},
  };
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('HomePage admin nav', () => {
  it('shows the admin panel link for ADMIN', () => {
    renderHome('ADMIN');
    const link = screen.getByRole('link', { name: /gestionar usuarios|administr|panel/i });
    expect(link.getAttribute('href')).toBe('/admin/usuarios');
  });

  it('does not show the admin panel link for USER', () => {
    renderHome('USER');
    expect(screen.queryByRole('link', { name: /gestionar usuarios|administr|panel/i })).toBeNull();
  });
});
