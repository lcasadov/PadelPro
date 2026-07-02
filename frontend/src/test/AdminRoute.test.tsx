// 6.1 — AdminRoute guard (TDD RED)
// D6: envuelve rutas que exigen role === 'ADMIN'. Un USER es redirigido; un
// ADMIN pasa. Un no autenticado va a /login.
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { AdminRoute } from '../guards/AdminRoute';

function renderWithRole(role: string | null, isAuthenticated: boolean) {
  const value = {
    accessToken: isAuthenticated ? 'tok' : null,
    role,
    mustChangePassword: false,
    isAuthenticated,
    setAccessToken: () => {},
    setSession: () => {},
    clearMustChangePassword: () => {},
  };
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={['/admin/usuarios']}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route path="/home" element={<div>Home Page</div>} />
          <Route element={<AdminRoute />}>
            <Route path="/admin/usuarios" element={<div>Admin Panel</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('AdminRoute', () => {
  it('lets an ADMIN through to the protected route', () => {
    renderWithRole('ADMIN', true);
    expect(screen.getByText('Admin Panel')).toBeDefined();
  });

  it('redirects a USER away from the admin route (to /home)', () => {
    renderWithRole('USER', true);
    expect(screen.queryByText('Admin Panel')).toBeNull();
    expect(screen.getByText('Home Page')).toBeDefined();
  });

  it('redirects an unauthenticated visitor to /login', () => {
    renderWithRole(null, false);
    expect(screen.queryByText('Admin Panel')).toBeNull();
    expect(screen.getByText('Login Page')).toBeDefined();
  });
});
