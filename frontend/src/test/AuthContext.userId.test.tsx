// reservas-ui-jugador (Grupo 3, D8) — AuthContext.userId (TDD RED).
// El área de reservas necesita el id numérico del usuario para comparar
// me.id == ownerId y decidir el botón cancelar. Se carga de forma lazy vía
// usuariosApi.getMeApi(token) (una sola vez) y se limpia en logout. El token
// sigue viviendo solo en memoria (RN-AUTH-09).
import { render, screen, act, waitFor } from '@testing-library/react';
import { describe, it, expect, afterEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import { AuthProvider, useAuth } from '../context/AuthContext';

afterEach(() => server.resetHandlers());

function UserIdConsumer() {
  const { userId, setSession, setAccessToken, loadUserId } = useAuth();
  return (
    <div>
      <span data-testid="user-id">{userId ?? 'null'}</span>
      <button
        onClick={() => setSession({ accessToken: 'user.jwt.token', role: 'USER', mustChangePassword: false })}
      >
        Login
      </button>
      <button onClick={() => void loadUserId()}>Load</button>
      <button onClick={() => setAccessToken(null)}>Logout</button>
    </div>
  );
}

describe('AuthContext userId (reservas-ui-jugador D8)', () => {
  it('empieza en null', () => {
    render(
      <AuthProvider>
        <UserIdConsumer />
      </AuthProvider>
    );
    expect(screen.getByTestId('user-id')).toHaveTextContent('null');
  });

  it('loadUserId llama a getMeApi una sola vez y expone userId', async () => {
    let calls = 0;
    server.use(
      http.get('/api/usuarios/me', ({ request }) => {
        calls += 1;
        expect(request.headers.get('Authorization')).toBe('Bearer user.jwt.token');
        return HttpResponse.json({
          id: 42,
          login: 'john',
          firstName: 'John',
          lastName: 'Doe',
          email: 'john@test.com',
          phone: null,
          status: 'ACTIVE',
          role: 'USER',
          telegramLinked: false,
        });
      })
    );

    render(
      <AuthProvider>
        <UserIdConsumer />
      </AuthProvider>
    );

    await act(async () => {
      screen.getByRole('button', { name: /Login/i }).click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /Load/i }).click();
    });
    await waitFor(() => expect(screen.getByTestId('user-id')).toHaveTextContent('42'));

    // Segunda invocación no vuelve a pedir al backend (idempotente).
    await act(async () => {
      screen.getByRole('button', { name: /Load/i }).click();
    });
    await waitFor(() => expect(calls).toBe(1));
    expect(screen.getByTestId('user-id')).toHaveTextContent('42');
  });

  it('logout limpia userId', async () => {
    server.use(
      http.get('/api/usuarios/me', () =>
        HttpResponse.json({
          id: 42,
          login: 'john',
          firstName: 'John',
          lastName: 'Doe',
          email: 'john@test.com',
          phone: null,
          status: 'ACTIVE',
          role: 'USER',
          telegramLinked: false,
        })
      )
    );

    render(
      <AuthProvider>
        <UserIdConsumer />
      </AuthProvider>
    );

    await act(async () => {
      screen.getByRole('button', { name: /Login/i }).click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /Load/i }).click();
    });
    await waitFor(() => expect(screen.getByTestId('user-id')).toHaveTextContent('42'));

    await act(async () => {
      screen.getByRole('button', { name: /Logout/i }).click();
    });
    expect(screen.getByTestId('user-id')).toHaveTextContent('null');
  });
});
