// reservas-ui-jugador (Grupo 6, refactor 6.4) — badge de estado reutilizable.
// Traduce el estado de una reserva o de su pago a una etiqueta en español con un
// tono visual coherente. Reutilizable en "Mis reservas" (lista) y en el detalle.
import { ReservationStatus, PaymentStatus } from '../services/reservasApi';
import styles from './EstadoBadge.module.css';

type Tono = 'ok' | 'pendiente' | 'cancelado' | 'neutro';

interface Meta {
  label: string;
  tono: Tono;
}

const RESERVA_META: Record<ReservationStatus, Meta> = {
  PENDING_CONFIRMATION: { label: 'Pendiente de confirmación', tono: 'pendiente' },
  CONFIRMED: { label: 'Confirmada', tono: 'ok' },
  CANCELLED: { label: 'Cancelada', tono: 'cancelado' },
  COMPLETED: { label: 'Completada', tono: 'neutro' },
};

const PAGO_META: Record<PaymentStatus, Meta> = {
  PENDING: { label: 'Pago pendiente', tono: 'pendiente' },
  IN_PROGRESS: { label: 'Pago en curso', tono: 'pendiente' },
  PAID: { label: 'Pagado', tono: 'ok' },
  FAILED: { label: 'Pago fallido', tono: 'cancelado' },
  CANCELLED: { label: 'Pago cancelado', tono: 'cancelado' },
  REFUNDED: { label: 'Reembolsado', tono: 'neutro' },
};

type EstadoBadgeProps =
  | { kind: 'reserva'; status: ReservationStatus }
  | { kind: 'pago'; status: PaymentStatus };

export function EstadoBadge(props: EstadoBadgeProps) {
  const meta =
    props.kind === 'reserva' ? RESERVA_META[props.status] : PAGO_META[props.status];
  // Fail-safe ante un estado no mapeado: mostrar el valor crudo con tono neutro.
  const label = meta?.label ?? props.status;
  const tono = meta?.tono ?? 'neutro';
  return <span className={`${styles.badge} ${styles[tono]}`}>{label}</span>;
}
