// acceso-cuenta-prod — AuthContext session extension (role + mustChangePassword)
// TDD RED: fails until AuthContext exposes role, mustChangePassword and setSession.
import { render, screen, act } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { AuthProvider, useAuth } from '../context/AuthContext';

function SessionConsumer() {
  const { role, mustChangePassword, setSession, clearMustChangePassword } = useAuth();
  return (
    <div>
      <span data-testid="role">{role ?? 'null'}</span>
      <span data-testid="must-change">{mustChangePassword ? 'yes' : 'no'}</span>
      <button
        onClick={() =>
          setSession({ accessToken: 'tok', role: 'ADMIN', mustChangePassword: true })
        }
      >
        Login Admin
      </button>
      <button onClick={() => clearMustChangePassword()}>Clear Must Change</button>
    </div>
  );
}

describe('AuthContext session (acceso-cuenta-prod)', () => {
  it('starts with null role and mustChangePassword false', () => {
    render(
      <AuthProvider>
        <SessionConsumer />
      </AuthProvider>
    );
    expect(screen.getByTestId('role')).toHaveTextContent('null');
    expect(screen.getByTestId('must-change')).toHaveTextContent('no');
  });

  it('setSession stores role and mustChangePassword flag together', async () => {
    render(
      <AuthProvider>
        <SessionConsumer />
      </AuthProvider>
    );
    await act(async () => {
      screen.getByRole('button', { name: /Login Admin/i }).click();
    });
    expect(screen.getByTestId('role')).toHaveTextContent('ADMIN');
    expect(screen.getByTestId('must-change')).toHaveTextContent('yes');
  });

  it('clearMustChangePassword resets only the flag, keeps role', async () => {
    render(
      <AuthProvider>
        <SessionConsumer />
      </AuthProvider>
    );
    await act(async () => {
      screen.getByRole('button', { name: /Login Admin/i }).click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /Clear Must Change/i }).click();
    });
    expect(screen.getByTestId('must-change')).toHaveTextContent('no');
    expect(screen.getByTestId('role')).toHaveTextContent('ADMIN');
  });
});
