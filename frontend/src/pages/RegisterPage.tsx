// T-028 — RegisterPage — Oleada 3: conectada a registerApi
// Ref visual: docs/ux/mockups/08-crear-cuenta.html
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { registerApi } from '../services/authApi';
import type { ApiError } from '../services/authApi';
import axios from 'axios';
import { PasswordStrengthIndicator } from '../components/PasswordStrengthIndicator';
import './pages.css';
import styles from './RegisterPage.module.css';

const PASSWORD_ERROR_MESSAGES: Record<string, string> = {
  MIN_LENGTH_8: 'La contraseña debe tener al menos 8 caracteres',
  REQUIRES_UPPERCASE: 'La contraseña debe contener al menos una mayúscula',
  REQUIRES_NUMBER: 'La contraseña debe contener al menos un número',
};

export function RegisterPage() {
  const navigate = useNavigate();

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [registered, setRegistered] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setErrorMessage(null);

    // Manual required-field guard (form has noValidate)
    if (!firstName || !lastName || !email || !password) {
      return;
    }

    try {
      await registerApi({ firstName, lastName, email, password });
      setRegistered(true);
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        const body = err.response?.data as ApiError | undefined;
        if (status === 409) {
          setErrorMessage('Este email ya está registrado. ¿Ya tienes cuenta?');
        } else if (status === 400 && body?.error === 'INVALID_PASSWORD') {
          const detail = body.details?.[0];
          const msg = detail
            ? (PASSWORD_ERROR_MESSAGES[detail] ?? 'Contraseña no válida')
            : 'Contraseña no válida';
          setErrorMessage(msg);
        } else if (status === 429) {
          setErrorMessage('Demasiados intentos. Inténtalo más tarde.');
        } else {
          setErrorMessage('Error inesperado. Inténtalo de nuevo.');
        }
      } else {
        setErrorMessage('Error inesperado. Inténtalo de nuevo.');
      }
    }
  }

  // Pantalla de confirmación tras registro exitoso (R-1.1)
  if (registered) {
    return (
      <div className={styles.registerPage}>
        <div className={styles.hero}>
          <h1 className={styles.headline}>
            ¡Cuenta creada!
          </h1>
          <p className={styles.step}>
            Tu cuenta queda <strong>pendiente de aprobación</strong> por el
            administrador del club.
          </p>
          <p className={styles.step}>
            Mientras tanto, tienes <strong>acceso provisional durante 2 días</strong>{' '}
            para empezar a usar la app.
          </p>
          <Link to="/login" className="p-link">
            Volver al inicio de sesión
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.registerPage}>
      {/* Back navigation */}
      <button
        type="button"
        className={styles.back}
        onClick={() => navigate(-1)}
        aria-label="Volver"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.5} width={20} height={20} aria-hidden="true">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>

      {/* Hero */}
      <div className={styles.hero}>
        <span className={styles.step}>Paso 1 de 1</span>
        <h1 className={styles.headline}>
          Crea tu<br />
          <em>cuenta.</em>
        </h1>
      </div>

      {/* Form */}
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <div className="p-input-group">
          <label className="p-input-label" htmlFor="reg-firstname">Nombre</label>
          <input
            id="reg-firstname"
            className="p-input"
            type="text"
            autoComplete="given-name"
            placeholder="Laura"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            required
          />
        </div>

        <div className="p-input-group">
          <label className="p-input-label" htmlFor="reg-lastname">Apellido</label>
          <input
            id="reg-lastname"
            className="p-input"
            type="text"
            autoComplete="family-name"
            placeholder="Casado"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            required
          />
        </div>

        <div className="p-input-group">
          <label className="p-input-label" htmlFor="reg-email">Email</label>
          <input
            id="reg-email"
            className="p-input"
            type="email"
            autoComplete="email"
            placeholder="tu@email.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>

        <div className="p-input-group">
          <label className="p-input-label" htmlFor="reg-password">Contraseña</label>
          <input
            id="reg-password"
            className="p-input"
            type="password"
            autoComplete="new-password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <PasswordStrengthIndicator password={password} />
        </div>

        {/* Error area — 400 (política contraseña) y 409 (email duplicado) */}
        {errorMessage && (
          <p className="p-error" role="alert">
            {errorMessage}
          </p>
        )}

        <button
          type="submit"
          className={`p-btn p-btn-primary ${styles.submitBtn}`}
        >
          Crear cuenta <span className="p-arrow" aria-hidden="true">→</span>
        </button>

        <p className={styles.loginLink}>
          ¿Ya tienes cuenta?{' '}
          <Link to="/login" className="p-link">
            Iniciar sesión
          </Link>
        </p>
      </form>
    </div>
  );
}
