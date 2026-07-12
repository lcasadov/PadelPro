// pagos-simulador-gestion (3.1) — SimuladorPagoPage.
// Cubre: APPROVED redirige a la pantalla OK; DECLINED muestra KO con reintento;
// formato de tarjeta inválido se bloquea en cliente (sin llamar al backend).
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { SimuladorPagoPage } from '../pages/SimuladorPagoPage';
import { server } from './mocks/server';

afterEach(() => server.resetHandlers());

function authValue() {
  return {
    accessToken: 'user.jwt.token',
    role: 'USER',
    mustChangePassword: false,
    userId: 42,
    isAuthenticated: true,
    setAccessToken: () => {},
    setSession: () => {},
    clearMustChangePassword: () => {},
    loadUserId: async () => {},
  };
}

function renderPage(entry = '/pagos/simulador?reservaId=r-1&importe=16') {
  return render(
    <AuthContext.Provider value={authValue()}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/pagos/simulador" element={<SimuladorPagoPage />} />
          <Route path="/pagos/ok" element={<div>PAGO CONFIRMADO OK</div>} />
          <Route path="/reservas/mias" element={<div>Mis reservas</div>} />
          <Route path="/login" element={<div>Login</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

async function rellenarTarjeta(numero: string, caducidad = '12/40', cvc = '123') {
  await userEvent.type(screen.getByLabelText('Número de tarjeta'), numero);
  await userEvent.type(screen.getByLabelText('Caducidad (MM/AA)'), caducidad);
  await userEvent.type(screen.getByLabelText('CVC'), cvc);
}

describe('SimuladorPagoPage', () => {
  it('muestra el importe y el aviso de pago simulado', () => {
    renderPage();
    expect(screen.getByText(/no se realiza ningún cargo real/i)).toBeInTheDocument();
    expect(screen.getByText('16,00 €')).toBeInTheDocument();
  });

  it('APPROVED → redirige a la pantalla de pago OK', async () => {
    server.use(
      http.post('/api/pagos/simular', () =>
        HttpResponse.json({ resultado: 'APPROVED' }, { status: 200 })
      )
    );
    renderPage();

    await rellenarTarjeta('4111111111111111');
    await userEvent.click(screen.getByRole('button', { name: /^pagar$/i }));

    expect(await screen.findByText(/pago confirmado ok/i)).toBeInTheDocument();
  });

  it('envía reservaId y datos de tarjeta al backend', async () => {
    let body: Record<string, unknown> = {};
    server.use(
      http.post('/api/pagos/simular', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ resultado: 'APPROVED' }, { status: 200 });
      })
    );
    renderPage();

    await rellenarTarjeta('4111 1111 1111 1111', '01/35', '456');
    await userEvent.click(screen.getByRole('button', { name: /^pagar$/i }));

    await screen.findByText(/pago confirmado ok/i);
    expect(body).toEqual({
      reservaId: 'r-1',
      cardNumber: '4111111111111111',
      expiry: '01/35',
      cvc: '456',
    });
  });

  it('DECLINED → muestra pantalla KO con opción de reintentar', async () => {
    server.use(
      http.post('/api/pagos/simular', () =>
        HttpResponse.json({ resultado: 'DECLINED', motivo: 'Fondos insuficientes.' }, { status: 200 })
      )
    );
    renderPage();

    await rellenarTarjeta('4000000000000002');
    await userEvent.click(screen.getByRole('button', { name: /^pagar$/i }));

    expect(await screen.findByText(/no se ha completado/i)).toBeInTheDocument();
    expect(screen.getByText(/fondos insuficientes/i)).toBeInTheDocument();

    // Reintentar vuelve al formulario.
    await userEvent.click(screen.getByRole('button', { name: /reintentar/i }));
    expect(screen.getByLabelText('Número de tarjeta')).toBeInTheDocument();
  });

  it('formato de tarjeta inválido → error en cliente sin llamar al backend', async () => {
    let called = false;
    server.use(
      http.post('/api/pagos/simular', () => {
        called = true;
        return HttpResponse.json({ resultado: 'APPROVED' });
      })
    );
    renderPage();

    await rellenarTarjeta('123'); // demasiado corta
    await userEvent.click(screen.getByRole('button', { name: /^pagar$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/número de tarjeta no es válido/i);
    expect(called).toBe(false);
  });

  it('caducidad vencida → error en cliente', async () => {
    renderPage();
    await rellenarTarjeta('4111111111111111', '01/20', '123');
    await userEvent.click(screen.getByRole('button', { name: /^pagar$/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/caducidad/i);
  });

  it('sin reservaId → mensaje de pago no disponible', () => {
    renderPage('/pagos/simulador');
    expect(screen.getByText(/pago no disponible/i)).toBeInTheDocument();
  });

  it('backend responde 409 (ya pagada) → error legible', async () => {
    server.use(
      http.post('/api/pagos/simular', () =>
        HttpResponse.json({ error: 'CONFLICT', message: 'ya pagada', timestamp: 't' }, { status: 409 })
      )
    );
    renderPage();

    await rellenarTarjeta('4111111111111111');
    await userEvent.click(screen.getByRole('button', { name: /^pagar$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/ya está pagada/i);
  });
});
