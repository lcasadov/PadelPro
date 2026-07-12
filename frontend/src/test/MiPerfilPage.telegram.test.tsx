// auth-otp-telegram (7.1 / 8.4) — sección Conexiones · Telegram de Mi Perfil.
// Cubre:
//   MT-1 — cuenta no vinculada: botón "Vincular" navega a la pantalla de vinculación
//   MT-2 — cuenta vinculada: "Desvincular" limpia la vinculación y muestra feedback
//   MT-3 — error al desvincular
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';

import { AuthContext } from '../context/AuthContext';
import { MiPerfilPage } from '../pages/MiPerfilPage';
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

function mockGetMe(telegramLinked: boolean) {
  server.use(
    http.get('/api/usuarios/me', () =>
      HttpResponse.json({ ...baseProfile, telegramLinked }, { status: 200 })
    )
  );
}

function mockUnlink200() {
  server.use(
    http.patch('/api/usuarios/me', () => new HttpResponse(null, { status: 200 }))
  );
}

function mockUnlink500() {
  server.use(
    http.patch('/api/usuarios/me', () =>
      HttpResponse.json({ error: 'INTERNAL', message: 'boom', timestamp: 'x' }, { status: 500 })
    )
  );
}

function mockInitiate200() {
  server.use(
    http.patch('/api/usuarios/me', () =>
      HttpResponse.json(
        {
          instructions: 'Envía /vincular 492715 al bot @PadelPro_bot',
          otpCode: '492715',
          expiresAt: new Date(Date.now() + 600_000).toISOString(),
        },
        { status: 200 }
      )
    )
  );
}

function renderPerfil(token: string | null) {
  const ctxValue = {
    accessToken: token,
    isAuthenticated: !!token,
    setAccessToken: () => {},
  };
  return render(
    <AuthContext.Provider value={ctxValue as never}>
      <MemoryRouter initialEntries={['/perfil']}>
        <Routes>
          <Route path="/perfil" element={<MiPerfilPage />} />
          <Route path={telegramPaths.vincular} element={<VincularTelegramPage />} />
          <Route path="/login" element={<div>Login Page</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

describe('MiPerfilPage · Conexiones Telegram (auth-otp-telegram 7.1)', () => {
  it('MT-1 — sin vincular: "Vincular" navega a la pantalla de vinculación', async () => {
    mockGetMe(false);
    // La pantalla de vinculación inicia la vinculación al montar.
    mockInitiate200();
    renderPerfil('test.jwt.token');

    const vincularBtn = await screen.findByRole('button', { name: /^vincular$/i });
    await userEvent.click(vincularBtn);

    expect(await screen.findByText(/Activa/i)).toBeInTheDocument();
  });

  it('MT-2 — vinculado: "Desvincular" limpia la vinculación', async () => {
    mockGetMe(true);
    renderPerfil('test.jwt.token');

    // Estado inicial: pill "Vinculado" + botón desvincular.
    expect(await screen.findByText('Vinculado')).toBeInTheDocument();
    const desvincularBtn = screen.getByRole('button', { name: /desvincular/i });

    mockUnlink200();
    await userEvent.click(desvincularBtn);

    expect(await screen.findByText(/telegram desvinculado/i)).toBeInTheDocument();
    // Ahora aparece el botón "Vincular".
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /^vincular$/i })).toBeInTheDocument()
    );
  });

  it('MT-3 — error al desvincular muestra alerta', async () => {
    mockGetMe(true);
    renderPerfil('test.jwt.token');

    const desvincularBtn = await screen.findByRole('button', { name: /desvincular/i });

    mockUnlink500();
    await userEvent.click(desvincularBtn);

    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudo desvincular/i);
  });
});
