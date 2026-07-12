// Grupo 5 — ConfirmarReservaPage (mockup 04).
// Muestra el tramo elegido (fecha/hora leídos de la query) y crea la reserva con
// POST /api/reservas. El precio total lo muestra tal cual lo devuelve el backend
// (priceTotal), NUNCA se calcula ni se envía desde el cliente (RN-RES-03).
//
// reservas-ui-jugador-fixes:
//  - D3: selector de duración 60/90/120 (default 60), acotado por `maxDuracion` de
//    la query (disponibilidad contigua real); se envía en `durationMinutes` y se
//    refleja en el resumen. El 409 del backend es la red de seguridad.
//  - D4/D5: cada participante adicional se añade con un conmutador socio/externo
//    (ParticipanteSelector); el payload es XOR (userId | externalName[+phone]).
//  - D2: 401 AUTH_REQUIRED → mensaje de sesión caducada + redirección a login. El
//    mapeo de error lee el contrato real (`error` como código, `details[0]` como
//    detalle); el genérico solo para 5xx sin código / error de red.
//
// Idempotency-Key (D2): se genera con `uuid()` por intento (crypto.randomUUID con
// fallback para contextos no seguros / HTTP — ver utils/uuid). Se reutiliza en
// reintentos de red del mismo envío (misma firma de formulario) y se regenera si
// el usuario cambia los datos (fecha/hora/duración/participantes).
import { useMemo, useRef, useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  crearReserva,
  isReservaApiError,
  CrearReservaPayload,
  ParticipanteAdicional,
  ReservaResponse,
  ReservaApiError,
} from '../services/reservasApi';
import { ParticipanteSelector } from '../components/ParticipanteSelector';
import {
  ParticipanteInput,
  emptyParticipante,
  participanteCompleto,
} from '../components/participanteModel';
import { reservasPaths } from './reservasPaths';
import { uuid } from '../utils/uuid';
import './pages.css';
import styles from './ConfirmarReservaPage.module.css';

/** Duraciones ofrecidas en la UI (D3). El backend acepta hasta 180, pero esta fase
 *  solo expone 60/90/120. */
const DURACIONES = [60, 90, 120] as const;
const DEFAULT_DURACION = 60;

/** Formatea un importe (number) como euros. El valor viene del backend. */
function formatPrecio(amount: number): string {
  return new Intl.NumberFormat('es-ES', {
    style: 'currency',
    currency: 'EUR',
  }).format(amount);
}

/** Traduce el error (por `code`) a copy accionable, leyendo el contrato real del
 *  backend (`details[0]` para VALIDATION_ERROR). El genérico queda reservado para
 *  fallos verdaderamente desconocidos. */
function messageForError(err: ReservaApiError): string {
  switch (err.code) {
    case 'AUTH_REQUIRED':
      return 'Tu sesión ha caducado. Vuelve a iniciar sesión.';
    case 'PARTICIPANTS_LIMIT_EXCEEDED':
      return 'Has superado el número máximo de participantes permitido.';
    case 'INVALID_STATE_TRANSITION':
      return 'La operación no es válida para el estado actual de la reserva.';
    case 'VALIDATION_ERROR': {
      const detalle = err.details[0] ?? err.fieldErrors[0]?.message;
      return detalle
        ? `Revisa los datos: ${detalle}`
        : 'Revisa los datos de la reserva; alguno no es válido e inténtalo de nuevo.';
    }
    case 'NOT_FOUND':
      return 'La franja ya no está disponible. Vuelve a la búsqueda para elegir otra.';
    case 'SERVER_ERROR':
      return 'Ha ocurrido un error en el servidor. Inténtalo de nuevo en unos momentos.';
    case 'NETWORK_ERROR':
      return 'No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo de nuevo.';
    default:
      return 'No se pudo completar la reserva. Inténtalo de nuevo.';
  }
}

/** Construye el participante XOR del payload a partir del input de la fila. */
function toPayloadParticipante(p: ParticipanteInput): ParticipanteAdicional | null {
  if (p.tipo === 'socio') {
    return p.userId != null ? { userId: p.userId } : null;
  }
  const externalName = p.externalName.trim();
  if (!externalName) return null;
  const externalPhone = p.externalPhone.trim();
  return externalPhone ? { externalName, externalPhone } : { externalName };
}

export function ConfirmarReservaPage() {
  const { accessToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const fecha = params.get('fecha') ?? '';
  const hora = params.get('hora') ?? '';
  const maxDuracion = Number(params.get('maxDuracion')) || 0;

  // Opciones de duración válidas para la franja (D3): acotadas por `maxDuracion`
  // (disponibilidad contigua). Sin `maxDuracion` se ofrecen todas.
  const opcionesDuracion = useMemo(
    () => DURACIONES.filter((d) => (maxDuracion > 0 ? d <= maxDuracion : true)),
    [maxDuracion]
  );

  const [duracion, setDuracion] = useState<number>(DEFAULT_DURACION);
  const [participantes, setParticipantes] = useState<ParticipanteInput[]>([]);
  const [notes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [reserva, setReserva] = useState<ReservaResponse | null>(null);
  const [conflict, setConflict] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // ── Idempotency-Key por intento (D2) ────────────────────────────────────────
  const participantesSig = participantes.map((p) =>
    p.tipo === 'socio' ? `s:${p.userId ?? ''}` : `e:${p.externalName.trim()}:${p.externalPhone.trim()}`
  );
  const signature = useMemo(
    () => JSON.stringify({ fecha, hora, duracion, notes: notes.trim(), participantesSig }),
    [fecha, hora, duracion, notes, participantesSig]
  );
  const idemRef = useRef<{ sig: string; key: string } | null>(null);

  function idempotencyKey(): string {
    if (!idemRef.current || idemRef.current.sig !== signature) {
      idemRef.current = { sig: signature, key: uuid() };
    }
    return idemRef.current.key;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  function addParticipante() {
    setParticipantes((prev) => [...prev, emptyParticipante()]);
  }

  function updateParticipante(index: number, next: ParticipanteInput) {
    setParticipantes((prev) => prev.map((p, i) => (i === index ? next : p)));
  }

  function removeParticipante(index: number) {
    setParticipantes((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleConfirm() {
    if (!accessToken) return;

    // Bloquea si algún participante está incompleto/ambiguo (XOR): socio sin
    // seleccionar o externo sin nombre.
    if (participantes.some((p) => !participanteCompleto(p))) {
      setConflict(false);
      setErrorMsg('Completa los datos de cada compañero: elige un socio o indica el nombre del invitado.');
      return;
    }

    setSubmitting(true);
    setErrorMsg(null);
    setConflict(false);

    const adicionales = participantes
      .map(toPayloadParticipante)
      .filter((p): p is ParticipanteAdicional => p !== null);

    // Payload sin importe alguno (RN-RES-03): el backend congela el precio.
    const payload: CrearReservaPayload = {
      reservationDate: fecha,
      startTime: hora,
      durationMinutes: duracion,
      ...(adicionales.length ? { participantesAdicionales: adicionales } : {}),
      ...(notes.trim() ? { notes: notes.trim() } : {}),
    };

    try {
      const created = await crearReserva(accessToken, payload, idempotencyKey());
      setReserva(created);
    } catch (err) {
      if (isReservaApiError(err) && err.code === 'AUTH_REQUIRED') {
        // Sesión caducada (D2): no reintentar la misma petición; re-autenticar.
        navigate('/login', { replace: true });
        return;
      }
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
        <button
          type="button"
          className={styles.homeBtn}
          onClick={() => navigate(reservasPaths.home)}
        >
          Inicio
        </button>
      </header>

      <div className={styles.titleBlock}>
        <span className={styles.eyebrow}>Confirmar reserva</span>
        <h1>
          Revisa y<br />
          <em>confirma.</em>
        </h1>
      </div>

      {/* Resumen del tramo elegido: fecha/hora de la query, duración elegida (D3) */}
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
          <span className={styles.summaryVal} data-testid="resumen-duracion">{duracion} min</span>
        </div>
      </div>

      {/* Selector de duración (D3) */}
      <div className={styles.durationBlock}>
        <p className={styles.sectionTitle} id="duracion-label">Duración</p>
        <div className={styles.durationOptions} role="group" aria-labelledby="duracion-label">
          {opcionesDuracion.map((d) => (
            <button
              key={d}
              type="button"
              className={`${styles.durationBtn} ${duracion === d ? styles.durationOn : ''}`}
              aria-pressed={duracion === d}
              onClick={() => setDuracion(d)}
            >
              {d} min
            </button>
          ))}
        </div>
      </div>

      {/* Participantes adicionales (D4/D5: socio registrado o invitado externo) */}
      <div className={styles.participantesBlock}>
        <p className={styles.sectionTitle}>Participantes adicionales</p>
        {participantes.map((p, i) => (
          <ParticipanteSelector
            key={i}
            index={i}
            value={p}
            token={accessToken ?? ''}
            onChange={(next) => updateParticipante(i, next)}
            onRemove={() => removeParticipante(i)}
          />
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
