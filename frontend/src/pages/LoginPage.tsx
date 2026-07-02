// T-027 — LoginPage — Oleada 3: conectada a authApi
// Ref visual: docs/ux/mockups/01-login.html
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { loginApi } from '../services/authApi';
import { useAuth } from '../context/AuthContext';
import axios from 'axios';
import './pages.css';
import styles from './LoginPage.module.css';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { setSession } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setErrorMessage(null);
    try {
      const response = await loginApi({ email, password });
      setSession({
        accessToken: response.access_token,
        role: response.role ?? null,
        mustChangePassword: response.must_change_password ?? false,
      });
      // D9 — cambio forzado: si la cuenta debe cambiar la contraseña (tras un
      // reset del admin), se redirige a la pantalla de cambio antes de entrar.
      if (response.must_change_password) {
        navigate('/cambiar-password');
      } else {
        navigate('/home');
      }
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        const body = err.response?.data as { error?: string } | undefined;
        if (status === 401) {
          // Anti-enumeración: mensaje genérico de credenciales.
          setErrorMessage('Credenciales inválidas');
        } else if (status === 403 && body?.error === 'ACCOUNT_NOT_ACTIVE') {
          // D5 — cuenta pendiente de aprobación o desactivada (distinto de credenciales).
          setErrorMessage(
            'Tu cuenta aún no está activada. Contacta con el administrador del club.'
          );
        } else if (status === 403) {
          setErrorMessage('Tu cuenta aún no está activada. Contacta con el administrador del club.');
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

  return (
    <div className={styles.loginPage}>
      {/* Hero section */}
      <div className={styles.hero}>
        <div className="p-glyph" aria-hidden="true">PP</div>
        <div className="p-logo" style={{ marginBottom: 'auto' }}>
          <span className="p-dot" />
          PadelPro
        </div>
        <h1 className={styles.headline}>
          Reserva, <em>juega</em>, vuelve.
        </h1>
        <p className={styles.sub}>Tu club, tus pistas y tus partidas en una sola app.</p>
      </div>

      {/* Form section */}
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <div className="p-input-group">
          <label className="p-input-label" htmlFor="login-email">Email</label>
          <input
            id="login-email"
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
          <label className="p-input-label" htmlFor="login-password">Contraseña</label>
          <input
            id="login-password"
            className="p-input"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>

        {errorMessage && (
          <p className="p-error" role="alert">
            {errorMessage}
          </p>
        )}

        <button type="submit" className={`p-btn p-btn-primary ${styles.submitBtn}`}>
          Entrar <span className="p-arrow" aria-hidden="true">→</span>
        </button>

        <div className={styles.footer}>
          <Link to="/forgot-password" className="p-link">
            ¿Olvidaste la contraseña?
          </Link>
          <Link to="/register" className="p-link">
            Crear cuenta
          </Link>
        </div>
      </form>
    </div>
  );
}
