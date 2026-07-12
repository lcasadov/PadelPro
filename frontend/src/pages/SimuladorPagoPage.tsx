// pagos-simulador-gestion (D1/D2) — SimuladorPagoPage (checkout simulado).
// Pantalla PROVISIONAL de pago online mientras Redsys no está configurado. Pide
// nº de tarjeta + caducidad (MM/AA) + CVC y llama a `POST /api/pagos/simular`:
//   - APPROVED → el backend ya marcó el pago PAID → redirige a la pantalla OK
//     (PagoConfirmadoPage consulta `GET /api/pagos` y muestra el estado real PAID).
//   - DECLINED → pantalla KO in-situ con opción de reintentar (no se ha cobrado).
// Copy claro de que es un PAGO SIMULADO. Los datos de tarjeta se validan también en
// cliente y NUNCA se almacenan (solo se envían para decidir el resultado, RN-RGPD-04).
import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { simularPago } from '../services/pagosApi';
import { isReservaApiError } from '../services/reservasApi';
import { reservasPaths, pagoRetornoPath } from './reservasPaths';
import './pages.css';
import styles from './SimuladorPagoPage.module.css';

function formatPrecio(amount: number): string {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(amount);
}

/** Quita separadores para validar/normalizar el número de tarjeta. */
function normalizeCard(value: string): string {
  return value.replace(/\s+/g, '');
}

/** Longitud de tarjeta plausible (13–19 dígitos, PAN estándar). */
function isTarjetaValida(value: string): boolean {
  return /^\d{13,19}$/.test(normalizeCard(value));
}

/** Caducidad "MM/AA" con mes 01–12 y no vencida (último día del mes ≥ hoy). */
function isCaducidadValida(value: string): boolean {
  const m = /^(\d{2})\/(\d{2})$/.exec(value.trim());
  if (!m) return false;
  const mes = Number(m[1]);
  const anio = Number(m[2]);
  if (mes < 1 || mes > 12) return false;
  // Fin del mes indicado (día 0 del mes siguiente) a las 23:59:59.
  const finMes = new Date(2000 + anio, mes, 0, 23, 59, 59);
  return finMes.getTime() >= Date.now();
}

/** CVC de 3 dígitos. */
function isCvcValido(value: string): boolean {
  return /^\d{3}$/.test(value.trim());
}

export function SimuladorPagoPage() {
  const { accessToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const state = (location.state as { reservaId?: string; importe?: number } | null) ?? null;
  const reservaId = state?.reservaId ?? searchParams.get('reservaId') ?? '';
  const importeParam = searchParams.get('importe');
  const importe = state?.importe ?? (importeParam != null ? Number(importeParam) : undefined);

  const [cardNumber, setCardNumber] = useState('');
  const [expiry, setExpiry] = useState('');
  const [cvc, setCvc] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [declined, setDeclined] = useState<{ motivo?: string } | null>(null);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Sin reserva no hay nada que pagar (p. ej. recarga directa sin query ni state).
  if (!reservaId) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <h1 className={styles.title}>Pago no disponible</h1>
          <p className={styles.notice}>
            No se ha podido iniciar el pago. Vuelve a tus reservas e inténtalo de nuevo.
          </p>
          <Link to={reservasPaths.mias} className="p-btn p-btn-primary">
            Volver a mis reservas
          </Link>
        </div>
      </div>
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!accessToken) return;

    // Validación de formato en cliente (el backend la repite: red de seguridad).
    if (!isTarjetaValida(cardNumber)) {
      setFormError('El número de tarjeta no es válido (13 a 19 dígitos).');
      return;
    }
    if (!isCaducidadValida(expiry)) {
      setFormError('La caducidad debe tener formato MM/AA y no estar vencida.');
      return;
    }
    if (!isCvcValido(cvc)) {
      setFormError('El CVC debe tener 3 dígitos.');
      return;
    }

    setFormError(null);
    setSubmitting(true);
    try {
      const res = await simularPago(accessToken, {
        reservaId,
        cardNumber: normalizeCard(cardNumber),
        expiry: expiry.trim(),
        cvc: cvc.trim(),
      });
      if (res.resultado === 'APPROVED') {
        // El backend ya marcó el pago PAID; la pantalla OK consulta el estado real.
        navigate(pagoRetornoPath(reservasPaths.pagoOk, reservaId));
      } else {
        setDeclined({ motivo: res.motivo });
      }
    } catch (err) {
      const msg =
        isReservaApiError(err) && (err.code === 'CONFLICT' || err.status === 422)
          ? 'Esta reserva ya está pagada.'
          : isReservaApiError(err) && err.code === 'FORBIDDEN'
            ? 'No puedes pagar esta reserva.'
            : isReservaApiError(err) && err.code === 'VALIDATION_ERROR'
              ? 'Los datos de la tarjeta no son válidos.'
              : 'No se pudo procesar el pago. Inténtalo de nuevo.';
      setFormError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  function reintentar() {
    setDeclined(null);
    setFormError(null);
    setCardNumber('');
    setExpiry('');
    setCvc('');
  }

  // Pantalla KO — el pago fue rechazado (no se ha cobrado). Reintento disponible.
  if (declined) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <div className={styles.failIco} aria-hidden="true">
            ✕
          </div>
          <h1 className={styles.title}>El pago no se ha completado</h1>
          <p className={styles.notice}>
            {declined.motivo ?? 'Tu tarjeta ha sido rechazada.'} No se ha realizado ningún
            cargo. Puedes intentarlo de nuevo con otra tarjeta.
          </p>
          <div className={styles.actions}>
            <button type="button" className="p-btn p-btn-primary" onClick={reintentar}>
              Reintentar
            </button>
            <Link to={reservasPaths.mias} className="p-btn p-btn-outline">
              Volver a mis reservas
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.merch}>
          <span className={styles.merchLogo}>PP</span>
          <div className={styles.merchInfo}>
            <span className={styles.merchName}>PadelPro</span>
            <span className={styles.merchUrl}>Pago simulado</span>
          </div>
          <span className={styles.secure} aria-hidden="true">
            SIMULADO
          </span>
        </div>

        <div className={styles.sandboxNotice} role="note">
          Este es un <strong>pago simulado</strong> (provisional). No se realiza ningún
          cargo real ni se almacenan los datos de tu tarjeta.
        </div>

        {importe != null && !Number.isNaN(importe) && (
          <div className={styles.amountBlock}>
            <span className={styles.amountLab}>Importe</span>
            <span className={styles.amount}>{formatPrecio(importe)}</span>
          </div>
        )}

        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          <label className={styles.field}>
            <span className={styles.label}>Número de tarjeta</span>
            <input
              className={styles.input}
              type="text"
              inputMode="numeric"
              autoComplete="cc-number"
              placeholder="4111 1111 1111 1111"
              value={cardNumber}
              onChange={(e) => setCardNumber(e.target.value)}
              aria-label="Número de tarjeta"
            />
          </label>

          <div className={styles.row}>
            <label className={styles.field}>
              <span className={styles.label}>Caducidad</span>
              <input
                className={styles.input}
                type="text"
                inputMode="numeric"
                autoComplete="cc-exp"
                placeholder="MM/AA"
                maxLength={5}
                value={expiry}
                onChange={(e) => setExpiry(e.target.value)}
                aria-label="Caducidad (MM/AA)"
              />
            </label>
            <label className={styles.field}>
              <span className={styles.label}>CVC</span>
              <input
                className={styles.input}
                type="text"
                inputMode="numeric"
                autoComplete="cc-csc"
                placeholder="123"
                maxLength={4}
                value={cvc}
                onChange={(e) => setCvc(e.target.value)}
                aria-label="CVC"
              />
            </label>
          </div>

          {formError && (
            <p className="p-error" role="alert">
              {formError}
            </p>
          )}

          <button type="submit" className="p-btn p-btn-primary" disabled={submitting}>
            {submitting ? 'Procesando…' : 'Pagar'}
          </button>
        </form>

        <details className={styles.help}>
          <summary className={styles.helpSummary}>Tarjetas de prueba</summary>
          <ul className={styles.helpList}>
            <li>
              <code>4111 1111 1111 1111</code> — aprueba siempre
            </li>
            <li>
              <code>4000 0000 0000 0002</code> — rechaza siempre
            </li>
            <li>Cualquier otra tarjeta válida → resultado aleatorio</li>
          </ul>
        </details>

        <Link to={reservasPaths.mias} className={styles.cancelLink}>
          Cancelar
        </Link>
      </div>
    </div>
  );
}
