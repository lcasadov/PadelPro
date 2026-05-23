// T-028 — RegisterPage (sin lógica de API — stub en Oleada 3)
// Ref visual: docs/ux/mockups/08-crear-cuenta.html
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PasswordStrengthIndicator } from '../components/PasswordStrengthIndicator';
import './pages.css';
import styles from './RegisterPage.module.css';

export function RegisterPage() {
  const navigate = useNavigate();

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setErrorMessage(null);
    // Stub — implementación real en Oleada 3
    console.log('register stub', { firstName, lastName, email, password });
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
