// reservas-ui-jugador-fixes (Grupo 3, D4/D5) — ParticipanteSelector — TDD.
// Conmutador socio/externo; modo socio consume GET /api/usuarios/buscar y fija
// userId; modo externo captura nombre/teléfono. Se prueba el buscador con MSW y
// la exclusividad XOR (cambiar de modo limpia el lado contrario).
import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './mocks/server';
import { ParticipanteSelector } from '../components/ParticipanteSelector';
import {
  ParticipanteInput,
  emptyParticipante,
  participanteCompleto,
} from '../components/participanteModel';

afterEach(() => server.resetHandlers());

// Wrapper con estado que expone el último valor vía callback para las aserciones.
function Harness({ onValue }: { onValue?: (v: ParticipanteInput) => void }) {
  const [value, setValue] = useState<ParticipanteInput>(emptyParticipante());
  return (
    <ParticipanteSelector
      index={0}
      value={value}
      token="user.jwt.token"
      onChange={(n) => {
        onValue?.(n);
        setValue(n);
      }}
      onRemove={() => {}}
    />
  );
}

describe('ParticipanteSelector (Grupo 3)', () => {
  it('modo socio: el buscador consulta /api/usuarios/buscar y muestra resultados', async () => {
    let capturedQ = '';
    server.use(
      http.get('/api/usuarios/buscar', ({ request }) => {
        capturedQ = new URL(request.url).searchParams.get('q') ?? '';
        return HttpResponse.json([
          { id: 7, nombre: 'Ana García' },
          { id: 9, nombre: 'Ana Ruiz' },
        ]);
      })
    );

    render(<Harness />);
    await userEvent.click(screen.getByRole('button', { name: /socio registrado/i }));
    await userEvent.type(screen.getByLabelText(/buscar socio/i), 'Ana');

    expect(await screen.findByText('Ana García')).toBeInTheDocument();
    expect(screen.getByText('Ana Ruiz')).toBeInTheDocument();
    expect(capturedQ).toBe('Ana');
  });

  it('seleccionar un socio fija userId y limpia el lado externo (XOR)', async () => {
    server.use(
      http.get('/api/usuarios/buscar', () => HttpResponse.json([{ id: 7, nombre: 'Ana García' }]))
    );

    let latest: ParticipanteInput | null = null;
    render(<Harness onValue={(v) => (latest = v)} />);

    await userEvent.click(screen.getByRole('button', { name: /socio registrado/i }));
    await userEvent.type(screen.getByLabelText(/buscar socio/i), 'Ana');
    await userEvent.click(await screen.findByRole('button', { name: 'Ana García' }));

    expect(latest).not.toBeNull();
    expect(latest!.userId).toBe(7);
    expect(latest!.externalName).toBe('');
    expect(participanteCompleto(latest!)).toBe(true);
  });

  it('término corto (<2) no dispara la búsqueda', async () => {
    const spy = vi.fn();
    server.use(
      http.get('/api/usuarios/buscar', () => {
        spy();
        return HttpResponse.json([]);
      })
    );

    render(<Harness />);
    await userEvent.click(screen.getByRole('button', { name: /socio registrado/i }));
    await userEvent.type(screen.getByLabelText(/buscar socio/i), 'A');

    // Margen para un posible debounce; confirmamos que no se llamó.
    await new Promise((r) => setTimeout(r, 350));
    expect(spy).not.toHaveBeenCalled();
  });

  it('modo externo captura nombre y teléfono (sin userId)', async () => {
    let latest: ParticipanteInput | null = null;
    render(<Harness onValue={(v) => (latest = v)} />);

    await userEvent.type(screen.getByLabelText(/nombre del compañero/i), 'Marcos R.');
    await userEvent.type(screen.getByLabelText(/teléfono/i), '600111222');

    expect(latest).not.toBeNull();
    expect(latest!.externalName).toBe('Marcos R.');
    expect(latest!.externalPhone).toBe('600111222');
    expect(latest!.userId).toBeNull();
  });
});
