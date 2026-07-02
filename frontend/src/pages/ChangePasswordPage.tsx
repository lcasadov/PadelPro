// 7.4 — ChangePasswordPage (D9): cambio de contraseña forzado tras un reset.
// Cuando el login devuelve must_change_password=true, el usuario aterriza aquí
// y no puede usar la app hasta cambiarla. Usa POST /api/usuarios/me/password.
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { changeMyPasswordApi } from '../services/usuariosApi';
import type { ApiError } from '../services/authApi';
import { PasswordStrengthIndicator } from '../components/PasswordStrengthIndicator';
import './pages.css';
import styles from './ChangePasswordPage.module.css';

const PASSWORD_ERROR_MESSAGES: Record<string, string> = {
  MIN_LENGTH_8: 'La contraseña debe tener al menos 8 caracteres',
  REQUIRES_UPPERCASE: 'La contraseña debe contener al menos una mayúscula',
  REQUIRES_NUMBER: 'La contraseña debe contener al menos un número',
};

export function ChangePasswordPage() {
  const { accessToken, mustChangePassword, clearMustChangePassword } = useAuth();
  const navigate = useNavigate();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setErrorMessage(null);

    if (!currentPassword || !newPassword || !confirmPassword) {
      setErrorMessage('Rellena todos los campos.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setErrorMessage('Las contraseñas nuevas no coinciden.');
      return;
    }
    if (!accessToken) return;

    setSubmitting(true);
    try {
      await changeMyPasswordApi(accessToken, {
        current_password: currentPassword,
        new_password: newPassword,
      });
      clearMustChangePassword();
      navigate('/home');
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        const body = err.response?.data as ApiError | undefined;
        if (status === 401) {
          setErrorMessage('La contraseña actual es incorrecta.');
        } else if (status === 400 && body?.error === 'INVALID_PASSWORD') {
          const detail = body.details?.[0];
          setErrorMessage(
            detail ? (PASSWORD_ERROR_MESSAGES[detail] ?? 'Contraseña no válida') : 'Contraseña no válida'
          );
        } else {
          setErrorMessage('Error inesperado. Inténtalo de nuevo.');
        }
      } else {
        setErrorMessage('Error inesperado. Inténtalo de nuevo.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.logo}>
        <span className="p-dot" />
        PadelPro
      </div>

      <div className={styles.hero}>
        <span className={styles.eyebrow}>Seguridad</span>
        <h1 className={styles.headline}>
          Cambia tu<br />
          <em>contraseña.</em>
        </h1>
        {mustChangePassword && (
          <p className={styles.notice}>
            Estás usando una contraseña temporal. Por seguridad, debes establecer
            una nueva antes de continuar.
          </p>
        )}
      </div>

      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <div className="p-input-group">
          <label className="p-input-label" htmlFor="cp-current">Contraseña actual</label>
          <input
            id="cp-current"
            className="p-input"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
          />
        </div>

        <div className="p-input-group">
          <label className="p-input-label" htmlFor="cp-new">Nueva contraseña</label>
          <input
            id="cp-new"
            className="p-input"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
          />
          <PasswordStrengthIndicator password={newPassword} />
        </div>

        <div className="p-input-group">
          <label className="p-input-label" htmlFor="cp-confirm">Confirmar nueva contraseña</label>
          <input
            id="cp-confirm"
            className="p-input"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          />
        </div>

        {errorMessage && (
          <p className="p-error" role="alert">
            {errorMessage}
          </p>
        )}

        <button
          type="submit"
          className="p-btn p-btn-primary"
          disabled={submitting}
        >
          {submitting ? 'Guardando…' : 'Cambiar contraseña'}
          <span className="p-arrow" aria-hidden="true">→</span>
        </button>
      </form>
    </div>
  );
}
