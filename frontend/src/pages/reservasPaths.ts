// reservas-ui-jugador — rutas del journey del jugador.
// El registro de rutas (App.tsx) es del Grupo 8; aquí solo se centralizan los
// paths (como strings) que usan las páginas para navegar entre sí, evitando
// literales duplicados y desincronizados.

export const reservasPaths = {
  /** Pantalla de Inicio (Home) del jugador — destino del control "volver a Inicio". */
  home: '/home',
  disponibilidad: '/reservas/disponibilidad',
  confirmar: '/reservas/confirmar',
  mias: '/reservas/mias',
  /** Detalle de una reserva concreta. */
  detalle: (id: string): string => `/reservas/detalle/${id}`,
} as const;

/**
 * Construye el path (string) de "Confirmar reserva" con el tramo elegido en la
 * query (fecha + hora + duración). La confirmación lee estos valores con
 * `useSearchParams` (D3: la UI consume tramos ya resueltos por el backend).
 *
 * `maxDuracion` (opcional, D3) acota la duración máxima ofrecida en el selector de
 * la confirmación según la disponibilidad contigua real de la franja; si se omite,
 * la confirmación ofrece todas las opciones (60/90/120) y confía en el 409 del
 * backend como red de seguridad.
 */
export function confirmarReservaPath(
  fecha: string,
  startTime: string,
  durationMinutes: number,
  maxDuracion?: number
): string {
  const params = new URLSearchParams({
    fecha,
    hora: startTime,
    duracion: String(durationMinutes),
  });
  if (maxDuracion != null) {
    params.set('maxDuracion', String(maxDuracion));
  }
  return `${reservasPaths.confirmar}?${params.toString()}`;
}
