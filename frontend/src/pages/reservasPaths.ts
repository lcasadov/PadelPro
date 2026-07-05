// reservas-ui-jugador — rutas del journey del jugador.
// El registro de rutas (App.tsx) es del Grupo 8; aquí solo se centralizan los
// paths (como strings) que usan las páginas para navegar entre sí, evitando
// literales duplicados y desincronizados.

export const reservasPaths = {
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
 */
export function confirmarReservaPath(
  fecha: string,
  startTime: string,
  durationMinutes: number
): string {
  const params = new URLSearchParams({
    fecha,
    hora: startTime,
    duracion: String(durationMinutes),
  });
  return `${reservasPaths.confirmar}?${params.toString()}`;
}
