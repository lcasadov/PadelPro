// Grupo 8 — Navegación y rutas (reservas-ui-jugador) — TDD.
// 1) El botón "Reservar pista" del Home lleva a la búsqueda de disponibilidad.
// 2) Las 4 rutas de reservas registradas en App están protegidas por PrivateRoute:
//    un usuario NO autenticado que intenta acceder es redirigido a /login.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { HomePage } from '../pages/HomePage';
import App from '../App';
import { reservasPaths } from '../pages/reservasPaths';

describe('HomePage → disponibilidad', () => {
  it('el botón "Reservar pista" navega a la búsqueda de disponibilidad', async () => {
    const user = userEvent.setup();
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/home']}>
          <Routes>
            <Route path="/home" element={<HomePage />} />
            <Route
              path={reservasPaths.disponibilidad}
              element={<div>Buscar pista (stub disponibilidad)</div>}
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await user.click(screen.getByRole('button', { name: /reservar pista/i }));

    expect(screen.getByText('Buscar pista (stub disponibilidad)')).toBeDefined();
  });
});

describe('Rutas de reservas protegidas por PrivateRoute (App)', () => {
  afterEach(() => {
    // Restaura la URL entre casos para no arrastrar estado de history.
    window.history.pushState({}, '', '/');
  });

  const rutasProtegidas = [
    reservasPaths.disponibilidad,
    reservasPaths.confirmar,
    reservasPaths.mias,
    reservasPaths.detalle('123'),
  ];

  it.each(rutasProtegidas)(
    'redirige a /login a un usuario no autenticado que accede a %s',
    (ruta) => {
      window.history.pushState({}, '', ruta);
      render(<App />);

      // PrivateRoute redirige a /login (Navigate replace).
      expect(window.location.pathname).toBe('/login');
      // La pantalla de login se ha renderizado (botón "Entrar").
      expect(screen.getByRole('button', { name: /entrar/i })).toBeDefined();
    }
  );
});
