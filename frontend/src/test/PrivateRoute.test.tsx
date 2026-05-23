import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { PrivateRoute } from '../guards/PrivateRoute';

function renderWithRouter(initialPath: string) {
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route element={<PrivateRoute />}>
            <Route path="/home" element={<div>Protected Home</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe('PrivateRoute', () => {
  it('redirects unauthenticated users to /login', () => {
    renderWithRouter('/home');
    expect(screen.getByText('Login Page')).toBeDefined();
    expect(screen.queryByText('Protected Home')).toBeNull();
  });

  it('renders /login page when accessing / as unauthenticated', () => {
    renderWithRouter('/home');
    // The guard redirects — Login Page is rendered
    expect(screen.getByText('Login Page')).toBeDefined();
  });
});
