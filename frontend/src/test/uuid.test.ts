// uuid() — helper de Idempotency-Key. Cubre el bug de prod (#201): en HTTP plano
// (contexto no seguro) `crypto.randomUUID` no existe y su uso lanzaba TypeError,
// abortando la creación de reserva antes del fetch.
import { describe, it, expect, afterEach, vi } from 'vitest';
import { uuid } from '../utils/uuid';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('uuid()', () => {
  it('usa crypto.randomUUID cuando está disponible (secure context)', () => {
    const spy = vi.fn(() => '11111111-1111-4111-8111-111111111111');
    vi.stubGlobal('crypto', { randomUUID: spy });

    expect(uuid()).toBe('11111111-1111-4111-8111-111111111111');
    expect(spy).toHaveBeenCalledOnce();
  });

  it('cae al fallback y NO lanza cuando crypto.randomUUID no existe (HTTP)', () => {
    // Reproduce el contexto no seguro del EC2: crypto sin randomUUID.
    vi.stubGlobal('crypto', {} as Crypto);

    let value = '';
    expect(() => {
      value = uuid();
    }).not.toThrow();
    expect(value).toMatch(UUID_V4);
  });

  it('cae al fallback cuando crypto es undefined', () => {
    vi.stubGlobal('crypto', undefined);

    expect(uuid()).toMatch(UUID_V4);
  });

  it('genera valores distintos en llamadas sucesivas', () => {
    vi.stubGlobal('crypto', {} as Crypto);
    expect(uuid()).not.toBe(uuid());
  });
});
