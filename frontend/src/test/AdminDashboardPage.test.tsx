// 5.4 — AdminDashboardPage — dashboard de administración del club.
// Cubre: carga inicial de métricas (ocupación + ingresos con desglose),
// cambio de rango con refetch, manejo de error de carga, y exportación CSV.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AuthContext } from '../context/AuthContext';
import { AdminDashboardPage } from '../pages/AdminDashboardPage';
import { server } from './mocks/server';

const adminValue = {
  accessToken: 'admin.jwt.token',
  role: 'ADMIN',
  mustChangePassword: false,
  isAuthenticated: true,
  setAccessToken: () => {},
  setSession: () => {},
  clearMustChangePassword: () => {},
};

function renderPage() {
  return render(
    <AuthContext.Provider value={adminValue}>
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

function ocupacionHandler(ocupacionPct: number) {
  return http.get('/api/admin/dashboard/ocupacion', () =>
    HttpResponse.json({
      fechaInicio: '2025-05-01',
      fechaFin: '2025-05-31',
      slotsReservados: 40,
      slotsDisponibles: 100,
      ocupacionPct,
    })
  );
}

function ingresosHandler(total: number) {
  return http.get('/api/admin/dashboard/ingresos', () =>
    HttpResponse.json({
      fechaInicio: '2025-05-01',
      fechaFin: '2025-05-31',
      total,
      porMetodo: { REDSYS: total * 0.8, CASH: total * 0.2 },
    })
  );
}

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    server.use(ocupacionHandler(40), ingresosHandler(1000));
  });

  it('5.3 — loads occupation and revenue metrics on mount', async () => {
    renderPage();

    // Ocupación: 40% + slots
    expect(await screen.findByText('40,0 %')).toBeDefined();
    const ocupCard = screen.getByLabelText('Ocupación');
    expect(within(ocupCard).getByText('40')).toBeDefined();
    expect(within(ocupCard).getByText('100')).toBeDefined();

    // Ingresos: total + desglose en euros (formato es-ES; el separador de
    // millares es opcional según el build ICU de Node).
    const ingCard = screen.getByLabelText('Ingresos');
    expect(within(ingCard).getByText(/1\.?000,00\s*€/)).toBeDefined();
    expect(within(ingCard).getByText(/800,00\s*€/)).toBeDefined();
    expect(within(ingCard).getByText(/200,00\s*€/)).toBeDefined();
  });

  it('5.1 — changing the date range refetches the metrics', async () => {
    renderPage();
    await screen.findByText('40,0 %');

    // Nuevo rango → el backend responde con otras cifras.
    server.use(ocupacionHandler(75), ingresosHandler(2000));

    await userEvent.clear(screen.getByLabelText(/hasta/i));
    await userEvent.type(screen.getByLabelText(/hasta/i), '2025-06-30');
    await userEvent.click(screen.getByRole('button', { name: /aplicar/i }));

    expect(await screen.findByText('75,0 %')).toBeDefined();
    const ingCard = screen.getByLabelText('Ingresos');
    expect(within(ingCard).getByText(/2\.?000,00\s*€/)).toBeDefined();
  });

  it('5.3 — shows an error message when metrics fail to load', async () => {
    server.use(
      http.get('/api/admin/dashboard/ocupacion', () => new HttpResponse(null, { status: 500 })),
      http.get('/api/admin/dashboard/ingresos', () => new HttpResponse(null, { status: 500 }))
    );
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudieron cargar/i);
  });

  describe('exportación CSV', () => {
    beforeEach(() => {
      // Añadimos los estáticos de blob al constructor URL real (no lo
      // reemplazamos: axios/MSW usan `new URL(...)` internamente).
      URL.createObjectURL = vi.fn(() => 'blob:mock');
      URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('5.3 — export button downloads the CSV for the selected range', async () => {
      let hit = false;
      server.use(
        http.get('/api/admin/dashboard/exportar', () => {
          hit = true;
          return new HttpResponse('fecha,reservas\n', {
            headers: {
              'Content-Type': 'text/csv',
              'Content-Disposition': 'attachment; filename="informe.csv"',
            },
          });
        })
      );
      const clickSpy = vi
        .spyOn(HTMLAnchorElement.prototype, 'click')
        .mockImplementation(() => {});

      renderPage();
      await screen.findByText('40,0 %');

      await userEvent.click(screen.getByRole('button', { name: /exportar csv/i }));

      await waitFor(() => expect(hit).toBe(true));
      expect(clickSpy).toHaveBeenCalledTimes(1);
    });

    it('5.3 — shows an error when the export fails', async () => {
      server.use(
        http.get('/api/admin/dashboard/exportar', () => new HttpResponse(null, { status: 500 }))
      );

      renderPage();
      await screen.findByText('40,0 %');

      await userEvent.click(screen.getByRole('button', { name: /exportar csv/i }));

      await waitFor(() =>
        expect(screen.getByText(/no se pudo generar el informe/i)).toBeDefined()
      );
    });
  });
});
