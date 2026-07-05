// Grupo 5 — ConfirmarReservaPage (mockup 04).
// Muestra el tramo elegido (fecha/hora/duración leídos de la query, D3) y crea la
// reserva con POST /api/reservas. El precio total lo muestra tal cual lo devuelve
// el backend (priceTotal), NUNCA se calcula ni se envía desde el cliente (RN-RES-03).
//
// Idempotency-Key (D2): se genera con crypto.randomUUID() por intento. Se reutiliza
// en reintentos de red del mismo envío (misma firma de formulario) y se regenera si
// el usuario cambia los datos (fecha/hora/duración/participantes).
//
// Errores por `code` (D6): 409 CONFLICT (D5, botón volver/refrescar), 400
// VALIDATION_ERROR, 422 PARTICIPANTS_LIMIT_EXCEEDED / INVALID_STATE_TRANSITION.
import { useMemo, useRef, useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  crearReserva,
  isReservaApiError,
  CrearReservaPayload,
  ReservaResponse,
  ReservaApiError,
} from '../services/reservasApi';
import { reservasPaths } from './reservasPaths';
import './pages.css';
import styles from './ConfirmarReservaPage.module.css';

interface ParticipanteInput {
  externalName: string;
}

/** Formatea un importe (number) como euros. El valor viene del backend. */
function formatPrecio(amount: number): string {
  return new Intl.NumberFormat('es-ES', {
    style: 'currency',
    currency: 'EUR',
  }).format(amount);
}

/** Traduce el error de negocio (por `code`) a copy accionable. `null` = error de
 *  franja ocupada, que se maneja como pantalla dedicada (D5). */
function messageForError(err: ReservaApiError): string {
  switch (err.code) {
    case 'PARTICIPANTS_LIMIT_EXCEEDED':
      return 'Has superado el número máximo de participantes permitido.';
    case 'INVALID_STATE_TRANSITION':
      return 'La operación no es válida para el estado actual de la reserva.';
    case 'VALIDATION_ERROR': {
      const first = err.fieldErrors[0];
      return first
        ? `Revisa los datos: ${first.message}`
        : 'Revisa los datos de la reserva e inténtalo de nuevo.';
    }
    default:
      return 'No se pudo completar la reserva. Inténtalo de nuevo.';
  }
}

export function ConfirmarReservaPage() {
  const { accessToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const fecha = params.get('fecha') ?? '';
  const hora = params.get('hora') ?? '';
  const duracion = params.get('duracion') ?? '';

  const [participantes, setParticipantes] = useState<ParticipanteInput[]>([]);
  // El campo de notas aún no tiene UI (pendiente en Grupo 5); se mantiene el valor
  // por defecto vacío para que el payload lo omita (notes.trim() === '').
  const [notes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [reserva, setReserva] = useState<ReservaResponse | null>(null);
  const [conflict, setConflict] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // ── Idempotency-Key por intento (D2) ────────────────────────────────────────
  // La firma resume los datos del formulario; si cambia, se regenera la key.
  const nombresParticipantes = participantes.map((p) => p.externalName.trim());
  const signature = useMemo(
    () => JSON.stringify({ fecha, hora, duracion, notes: notes.trim(), nombresParticipantes }),
    [fecha, hora, duracion, notes, nombresParticipantes]
  );
  const idemRef = useRef<{ sig: string; key: string } | null>(null);

  function idempotencyKey(): string {
    if (!idemRef.current || idemRef.current.sig !== signature) {
      idemRef.current = { sig: signature, key: crypto.randomUUID() };
    }
    return idemRef.current.key;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  function addParticipante() {
    setParticipantes((prev) => [...prev, { externalName: '' }]);
  }

  function updateParticipante(index: number, value: string) {
    setParticipantes((prev) =>
      prev.map((p, i) => (i === index ? { externalName: value } : p))
    );
  }

  function removeParticipante(index: number) {
    setParticipantes((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleConfirm() {
    if (!accessToken) return;
    setSubmitting(true);
    setErrorMsg(null);
    setConflict(false);

    const adicionales = participantes
      .map((p) => p.externalName.trim())
      .filter(Boolean)
      .map((externalName) => ({ externalName }));

    // Payload sin importe alguno (RN-RES-03): el backend congela el precio.
    const payload: CrearReservaPayload = {
      reservationDate: fecha,
      startTime: hora,
      durationMinutes: Number(duracion),
      ...(adicionales.length ? { participantesAdicionales: adicionales } : {}),
      ...(notes.trim() ? { notes: notes.trim() } : {}),
    };

    try {
      const created = await crearReserva(accessToken, payload, idempotencyKey());
      setReserva(created);
    } catch (err) {
      if (isReservaApiError(err) && err.code === 'CONFLICT') {
        setConflict(true);
      } else if (isReservaApiError(err)) {
        setErrorMsg(messageForError(err));
      } else {
        setErrorMsg('No se pudo completar la reserva. Inténtalo de nuevo.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  // ── Pantalla de éxito: reserva pendiente de confirmación por el club (sin pago) ──
  if (reserva) {
    return (
      <div className={styles.page}>
        <div className={styles.successCard} role="status">
          <div className={styles.successMark} aria-hidden="true">✓</div>
          <h1 className={styles.successTitle}>Reserva pendiente de confirmación por el club</h1>
          <p className={styles.successText}>
            Hemos registrado tu solicitud. El club la confirmará en breve; te avisaremos
            cuando esté confirmada.
          </p>
          <dl className={styles.successMeta}>
            <div>
              <dt>Fecha</dt>
              <dd>{reserva.reservationDate}</dd>
            </div>
            <div>
              <dt>Hora</dt>
              <dd>{reserva.startTime}</dd>
            </div>
            <div>
              <dt>Total</dt>
              <dd>{formatPrecio(reserva.priceTotal)}</dd>
            </div>
          </dl>
          <button
            type="button"
            className="p-btn p-btn-dark"
            onClick={() => navigate(reservasPaths.mias)}
          >
            Ver mis reservas
          </button>
        </div>
      </div>
    );
  }

  // ── Pantalla de conflicto (409): la franja se acaba de ocupar (D5) ──
  if (conflict) {
    return (
      <div className={styles.page}>
        <div className={styles.conflictCard} role="alert">
          <h1 className={styles.conflictTitle}>La franja se acaba de ocupar</h1>
          <p className={styles.conflictText}>
            Otro jugador ha reservado esta franja antes que tú. Vuelve a la búsqueda
            para elegir otra hora disponible.
          </p>
          <button
            type="button"
            className="p-btn p-btn-primary"
            onClick={() => navigate(reservasPaths.disponibilidad)}
          >
            Volver a buscar disponibilidad
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className="p-logo">
          <span className="p-dot" />
          PadelPro
        </div>
      </header>

      <div className={styles.titleBlock}>
        <span className={styles.eyebrow}>Confirmar reserva</span>
        <h1>
          Revisa y<br />
          <em>confirma.</em>
        </h1>
      </div>

      {/* Resumen del tramo elegido (D3): valores ya resueltos por el backend */}
      <div className={styles.summary}>
        <div className={styles.summaryRow}>
          <span className={styles.summaryLab}>Fecha</span>
          <span className={styles.summaryVal}>{fecha}</span>
        </div>
        <div className={styles.summaryRow}>
          <span className={styles.summaryLab}>Hora</span>
          <span className={styles.summaryVal}>{hora}</span>
        </div>
        <div className={styles.summaryRow}>
          <span className={styles.summaryLab}>Duración</span>
          <span className={styles.summaryVal}>{duracion} min</span>
        </div>
      </div>

      {/* Participantes adicionales (D4: solo externalName en v1) */}
      <div className={styles.participantesBlock}>
        <p className={styles.sectionTitle}>Participantes adicionales</p>
        {participantes.map((p, i) => (
          <div key={i} className={styles.participanteRow}>
            <label className={styles.fieldLabel} htmlFor={`participante-${i}`}>
              Nombre del compañero {i + 1}
            </label>
            <div className={styles.participanteInputRow}>
              <input
                id={`participante-${i}`}
                className={styles.textInput}
                type="text"
                value={p.externalName}
                onChange={(e) => updateParticipante(i, e.target.value)}
                placeholder="Nombre y apellido"
              />
              <button
                type="button"
                className={styles.removeBtn}
                onClick={() => removeParticipante(i)}
                aria-label={`Quitar compañero ${i + 1}`}
              >
                ×
              </button>
            </div>
          </div>
        ))}
        <button type="button" className={styles.addBtn} onClick={addParticipante}>
          + Añadir compañero
        </button>
      </div>

      {errorMsg && (
        <p className="p-error" role="alert">
          {errorMsg}
        </p>
      )}

      <button
        type="button"
        className={`p-btn p-btn-primary ${styles.confirmBtn}`}
        onClick={handleConfirm}
        disabled={submitting}
      >
        {submitting ? 'Reservando…' : errorMsg ? 'Reintentar' : 'Confirmar reserva'}
      </button>
    </div>
  );
}
