// reservas-ui-jugador-fixes (Grupo 3, D4/D5) — modelo del participante adicional.
// Tipos y helpers puros del selector socio/externo, separados del componente para
// no romper el fast-refresh (un fichero de componente solo debe exportar componentes).
export type TipoParticipante = 'socio' | 'externo';

export interface ParticipanteInput {
  tipo: TipoParticipante;
  externalName: string;
  externalPhone: string;
  userId: number | null;
  /** Nombre del socio seleccionado (solo para mostrar). */
  socioNombre: string;
}

/** Estado inicial de una fila de participante (modo externo por defecto). */
export function emptyParticipante(): ParticipanteInput {
  return { tipo: 'externo', externalName: '', externalPhone: '', userId: null, socioNombre: '' };
}

/** Un participante está completo si aporta exactamente uno de los dos lados (XOR). */
export function participanteCompleto(p: ParticipanteInput): boolean {
  return p.tipo === 'socio' ? p.userId != null : p.externalName.trim() !== '';
}
