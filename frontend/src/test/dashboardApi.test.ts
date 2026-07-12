// 5.4 — dashboardApi — validado contra MSW.
// Cubre: ocupación e ingresos (params + Authorization) y exportación CSV (blob +
// descarga con filename del Content-Disposition).
import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import { getOcupacion, getIngresos, exportarCsv } from '../services/dashboardApi';

const TOKEN = 'admin.jwt.token';
const RANGO = { fechaInicio: '2025-05-01', fechaFin: '2025-05-31' };

afterEach(() => server.resetHandlers());

describe('dashboardApi', () => {
  it('getOcupacion sends fechaInicio/fechaFin and Authorization header', async () => {
    let capturedUrl = '';
    let capturedAuth = '';
    server.use(
      http.get('/api/admin/dashboard/ocupacion', ({ request }) => {
        capturedUrl = request.url;
        capturedAuth = request.headers.get('Authorization') ?? '';
        return HttpResponse.json({
          fechaInicio: '2025-05-01',
          fechaFin: '2025-05-31',
          slotsReservados: 40,
          slotsDisponibles: 100,
          ocupacionPct: 40,
        });
      })
    );

    const result = await getOcupacion(TOKEN, RANGO);

    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    expect(capturedUrl).toContain('fechaInicio=2025-05-01');
    expect(capturedUrl).toContain('fechaFin=2025-05-31');
    expect(result.ocupacionPct).toBe(40);
    expect(result.slotsReservados).toBe(40);
  });

  it('getIngresos returns total and per-method breakdown', async () => {
    let capturedAuth = '';
    server.use(
      http.get('/api/admin/dashboard/ingresos', ({ request }) => {
        capturedAuth = request.headers.get('Authorization') ?? '';
        return HttpResponse.json({
          fechaInicio: '2025-05-01',
          fechaFin: '2025-05-31',
          total: 1250.5,
          porMetodo: { REDSYS: 1000.5, CASH: 250 },
        });
      })
    );

    const result = await getIngresos(TOKEN, RANGO);

    expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
    expect(result.total).toBe(1250.5);
    expect(result.porMetodo.REDSYS).toBe(1000.5);
    expect(result.porMetodo.CASH).toBe(250);
  });

  describe('exportarCsv', () => {
    beforeEach(() => {
      // jsdom no implementa createObjectURL/revokeObjectURL — los añadimos como
      // estáticos del constructor URL real (no lo reemplazamos: axios/MSW usan
      // `new URL(...)` internamente).
      URL.createObjectURL = vi.fn(() => 'blob:mock');
      URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('requests the CSV as a blob and triggers a download with the header filename', async () => {
      let capturedAuth = '';
      let capturedUrl = '';
      server.use(
        http.get('/api/admin/dashboard/exportar', ({ request }) => {
          capturedAuth = request.headers.get('Authorization') ?? '';
          capturedUrl = request.url;
          return new HttpResponse('fecha,reservas\n2025-05-01,3\n', {
            headers: {
              'Content-Type': 'text/csv',
              'Content-Disposition': 'attachment; filename="informe-uso-2025-05.csv"',
            },
          });
        })
      );

      const clickSpy = vi
        .spyOn(HTMLAnchorElement.prototype, 'click')
        .mockImplementation(() => {});

      await exportarCsv(TOKEN, RANGO);

      expect(capturedAuth).toBe(`Bearer ${TOKEN}`);
      expect(capturedUrl).toContain('fechaInicio=2025-05-01');
      expect(clickSpy).toHaveBeenCalledTimes(1);
      expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock');
    });

    it('falls back to a range-derived filename when no Content-Disposition is present', async () => {
      server.use(
        http.get('/api/admin/dashboard/exportar', () =>
          new HttpResponse('fecha,reservas\n', {
            headers: { 'Content-Type': 'text/csv' },
          })
        )
      );

      let downloadName = '';
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
        this: HTMLAnchorElement
      ) {
        downloadName = this.download;
      });

      await exportarCsv(TOKEN, RANGO);

      expect(downloadName).toBe('informe-uso-2025-05-01_2025-05-31.csv');
    });
  });
});
