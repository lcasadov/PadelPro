// Genera un UUID v4.
//
// `crypto.randomUUID()` SOLO está disponible en secure contexts (HTTPS o
// localhost). Cuando la app se sirve por HTTP plano (p. ej. el EC2 de pruebas
// en http://<ip>:5173) `crypto.randomUUID` es `undefined` y su invocación lanza
// un TypeError. Este helper lo usa cuando existe y, si no, cae a un generador
// v4 basado en Math.random — suficiente para un Idempotency-Key (no necesita
// garantías criptográficas, solo unicidad razonable por intento).
export function uuid(): string {
  const c = globalThis.crypto;
  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0;
    const v = ch === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
