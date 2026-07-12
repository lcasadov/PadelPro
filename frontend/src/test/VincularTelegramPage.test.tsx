// auth-otp-telegram (7.2 / 7.3 / 8.4) — VincularTelegramPage integration tests.
// Flujo REAL: PATCH LINK devuelve el otpCode que se MUESTRA; la vinculación la
// hace el webhook; "comprobar vinculación" re-consulta GET /api/usuarios/me.
// Cubre:
//   V-1 — inicia la vinculación al montar y muestra el otpCode + comando + bot
//   V-2 — "comprobar vinculación" con telegramLinked=true → éxito
//   V-3 — "comprobar vinculación" con telegramLinked=false → aviso de reintento
//   V-4 — código caducado → "Solicitar código nuevo" regenera el OTP
//   V-5 — error al iniciar la vinculación
//   V-6 — redirección a /login sin sesión
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';

import { AuthContext } from '../context/AuthContext';
import { VincularTelegramPage } from '../pages/VincularTelegramPage';
import { telegramPaths } from '../pages/telegramPaths';
import { server } from './mocks/server';

const baseProfile = {
  id: 42,
  login: 'lauracasado',
  firstName: 'Laura',
  lastName: 'Casado',
  email: 'laura.casado@email.com',
  phone: '+34612345678',
  status: 'ACTIVO',
  role: 'JUGADOR',
  telegramLinked: false,
};

/** PATCH LINK → devuelve el otpCode (contrato real TelegramLinkInstructionsResponse). */
function mockInitiate200(otpCode = '492715', expiresAtMs = Date.now() + 600_000) {
  server.use(
    http.patch('/api/usuarios/me', () =>
      HttpResponse.json(
        {
          instructions: `Envía /vincular ${otpCode} al bot @PadelPro_bot`,
          otpCode,
          expiresAt: new Date(expiresAtMs).toISOString(),
        },
        { status: 200 }
      )
    )
  );
}

function mockInitiate500() {
  server.use(
    http.patch('/api/usuarios/me', () =>
      HttpResponse.json({ error: 'INTERNAL', message: 'boom', timestamp: 'x' }, { status: 500 })
    )
  );
}

function mockGetMe(telegramLinked: boolean) {
  server.use(
    http.get('/api/usuarios/me', () =>
      HttpResponse.json({ ...baseProfile, telegramLinked }, { status: 200 })
    )
  );
}

function renderPage(token: string | null) {
  const ctxValue = {
    accessToken: token,
    isAuthenticated: !!token,
    setAccessToken: () => {},
  };
  return render(
    <AuthContext.Provider value={ctxValue as never}>
      <MemoryRouter initialEntries={[telegramPaths.vincular]}>
        <Routes>
          <Route path={telegramPaths.vincular} element={<VincularTelegramPage />} />
          <Route path="/perfil" element={<div>Mi Perfil</div>} />
          <Route path="/login" element={<div>Login Page</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('VincularTelegramPage (auth-otp-telegram 7.2/7.3)', () => {
  beforeEach(() => {
    mockInitiate200();
  });

  it('V-1 — muestra el otpCode, el comando y el bot', async () => {
    renderPage('test.jwt.token');

    expect(await screen.findByText(/Activa/i)).toBeInTheDocument();
    expect(screen.getByText('@PadelPro_bot')).toBeInTheDocument();
    // El código se muestra de forma prominente (es lo que se envía al bot).
    expect(await screen.findByText('492715')).toBeInTheDocument();
    // Contador TTL calculado desde expiresAt.
    expect(screen.getByText(/caduca en/i)).toBeInTheDocument();
    // El botón "Abrir Telegram" apunta al deep link del bot.
    const openLink = screen.getByRole('link', { name: /abrir telegram/i });
    expect(openLink).toHaveAttribute('href', 'https://t.me/PadelPro_bot');
  });

  it('V-2 — "comprobar vinculación" con telegramLinked=true muestra éxito', async () => {
    renderPage('test.jwt.token');

    const checkBtn = await screen.findByRole('button', {
      name: /ya lo envié — comprobar vinculación/i,
    });
    await waitFor(() => expect(checkBtn).not.toBeDisabled());

    mockGetMe(true);
    await userEvent.click(checkBtn);

    expect(await screen.findByText(/telegram vinculado/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /volver a mi perfil/i })).toBeInTheDocument();
  });

  it('V-3 — "comprobar vinculación" con telegramLinked=false avisa de reintento', async () => {
    renderPage('test.jwt.token');

    const checkBtn = await screen.findByRole('button', {
      name: /ya lo envié — comprobar vinculación/i,
    });
    await waitFor(() => expect(checkBtn).not.toBeDisabled());

    mockGetMe(false);
    await userEvent.click(checkBtn);

    expect(await screen.findByRole('alert')).toHaveTextContent(/aún no detectamos la vinculación/i);
  });

  it('V-4 — código caducado permite solicitar uno nuevo', async () => {
    // OTP ya caducado al montar → aparece "Solicitar código nuevo".
    mockInitiate200('111222', Date.now() - 1000);
    renderPage('test.jwt.token');

    const newCodeBtn = await screen.findByRole('button', { name: /solicitar código nuevo/i });
    expect(await screen.findByText(/el código ha caducado/i)).toBeInTheDocument();

    // Al solicitar uno nuevo, el backend devuelve un código vigente.
    mockInitiate200('333444', Date.now() + 600_000);
    await userEvent.click(newCodeBtn);

    expect(await screen.findByText('333444')).toBeInTheDocument();
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: /ya lo envié — comprobar vinculación/i })
      ).toBeInTheDocument()
    );
  });

  it('V-4b — error al comprobar la vinculación muestra alerta', async () => {
    renderPage('test.jwt.token');

    const checkBtn = await screen.findByRole('button', {
      name: /ya lo envié — comprobar vinculación/i,
    });
    await waitFor(() => expect(checkBtn).not.toBeDisabled());

    server.use(
      http.get('/api/usuarios/me', () =>
        HttpResponse.json({ error: 'INTERNAL', message: 'boom', timestamp: 'x' }, { status: 500 })
      )
    );
    await userEvent.click(checkBtn);

    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudo comprobar la vinculación/i);
  });

  it('V-5 — muestra error si falla el inicio de la vinculación', async () => {
    mockInitiate500();
    renderPage('test.jwt.token');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /no se pudo iniciar la vinculación/i
    );
  });

  it('V-6 — redirige a /login sin sesión', () => {
    renderPage(null);
    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });
});
