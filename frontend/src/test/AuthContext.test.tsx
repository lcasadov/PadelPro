import { render, screen, act } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { AuthProvider, useAuth } from '../context/AuthContext';

// Test component that uses the hook
function TestConsumer() {
  const { isAuthenticated, accessToken, setAccessToken } = useAuth();
  return (
    <div>
      <span data-testid="auth-status">{isAuthenticated ? 'authenticated' : 'unauthenticated'}</span>
      <span data-testid="token">{accessToken ?? 'null'}</span>
      <button onClick={() => setAccessToken('test-token')}>Set Token</button>
      <button onClick={() => setAccessToken(null)}>Clear Token</button>
    </div>
  );
}

describe('AuthContext', () => {
  it('starts unauthenticated with null token', () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    );
    expect(screen.getByTestId('auth-status')).toHaveTextContent('unauthenticated');
    expect(screen.getByTestId('token')).toHaveTextContent('null');
  });

  it('becomes authenticated when token is set', async () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    );

    await act(async () => {
      screen.getByRole('button', { name: /Set Token/i }).click();
    });

    expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated');
    expect(screen.getByTestId('token')).toHaveTextContent('test-token');
  });

  it('clears authentication when token is set to null', async () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    );

    await act(async () => {
      screen.getByRole('button', { name: /Set Token/i }).click();
    });

    await act(async () => {
      screen.getByRole('button', { name: /Clear Token/i }).click();
    });

    expect(screen.getByTestId('auth-status')).toHaveTextContent('unauthenticated');
    expect(screen.getByTestId('token')).toHaveTextContent('null');
  });

  it('throws if useAuth is used outside AuthProvider', () => {
    // Suppress console.error for this test
    const originalError = console.error;
    console.error = () => {};

    expect(() => render(<TestConsumer />)).toThrow(
      'useAuth must be used within AuthProvider'
    );

    console.error = originalError;
  });
});
