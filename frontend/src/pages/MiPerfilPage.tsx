// T-144 — MiPerfilPage
// Ref visual: docs/ux/mockups/14-mi-perfil.html
// API: GET /api/usuarios/me, PATCH /api/usuarios/me
import { useState, useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { getMeApi, updateMeApi, UsuarioMe } from '../services/usuariosApi';
import './pages.css';
import styles from './MiPerfilPage.module.css';

function getInitials(firstName: string, lastName: string): string {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();
}

export function MiPerfilPage() {
  const { accessToken, isAuthenticated } = useAuth();

  const [profile, setProfile] = useState<UsuarioMe | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // Form state — editable fields
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');

  // Feedback state
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Guard placed after all hooks — React rules require hooks to run unconditionally
  // The early return happens below, after the hook declarations.

  useEffect(() => {
    if (!accessToken) return;
    setLoading(true);
    getMeApi(accessToken)
      .then((data) => {
        setProfile(data);
        setFirstName(data.firstName);
        setLastName(data.lastName);
        setEmail(data.email);
        setPhone(data.phone ?? '');
      })
      .catch(() => {
        setErrorMsg('No se pudo cargar el perfil. Inténtalo de nuevo.');
      })
      .finally(() => setLoading(false));
  }, [accessToken]);

  // Guard: redirect unauthenticated users to /login (hooks already ran above)
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!accessToken) return;

    setSuccessMsg(null);
    setErrorMsg(null);
    setSubmitting(true);

    try {
      const updated = await updateMeApi(accessToken, {
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim(),
        phone: phone.trim() || null,
      });
      setProfile(updated);
      setFirstName(updated.firstName);
      setLastName(updated.lastName);
      setEmail(updated.email);
      setPhone(updated.phone ?? '');
      setSuccessMsg('Perfil actualizado');
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        const body = err.response?.data as { code?: string; message?: string } | undefined;
        if (status === 409 && body?.code === 'USUARIOS_EMAIL_CONFLICT') {
          setErrorMsg('El email ya está registrado');
        } else if (status === 400) {
          setErrorMsg('Los datos introducidos no son válidos. Revísalos e inténtalo de nuevo.');
        } else {
          setErrorMsg('Error inesperado. Inténtalo de nuevo.');
        }
      } else {
        setErrorMsg('Error inesperado. Inténtalo de nuevo.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={styles.perfilPage}>
      {/* ── Header ── */}
      <header className={styles.header}>
        <div className={styles.logo}>
          <span className={styles.dot} />
          PadelPro
        </div>
      </header>

      {loading ? (
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando perfil" />
        </div>
      ) : (
        <>
          {/* ── Avatar / identity block ── */}
          <div className={styles.profileHead}>
            <div className={styles.avatar} aria-hidden="true">
              {profile ? getInitials(profile.firstName, profile.lastName) : '??'}
            </div>
            <div className={styles.identityInfo}>
              <h1>
                {profile ? `${profile.firstName} ${profile.lastName}` : ''}
              </h1>
              {profile && (
                <div className={styles.loginHandle}>@{profile.login}</div>
              )}
            </div>
          </div>

          {/* ── Read-only badges: role, status, telegram ── */}
          {profile && (
            <div className={styles.badgeRow}>
              <span className={styles.badge}>{profile.role}</span>
              <span className={styles.badge}>{profile.status}</span>
              {profile.telegramLinked && (
                <span className={`${styles.badge} ${styles.badgeTelegram}`}>
                  Telegram vinculado
                </span>
              )}
            </div>
          )}

          <div className={styles.divider} />

          {/* ── Editable form ── */}
          <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <p className={styles.sectionTitle}>Datos personales</p>

            {successMsg && (
              <p className={styles.successMsg}>{successMsg}</p>
            )}

            {errorMsg && (
              <p className={styles.errorMsg} role="alert">{errorMsg}</p>
            )}

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="perfil-firstName">
                Nombre
              </label>
              <input
                id="perfil-firstName"
                className={styles.fieldInput}
                type="text"
                autoComplete="given-name"
                value={firstName}
                onChange={(e) => {
                  setFirstName(e.target.value);
                  setSuccessMsg(null);
                }}
                required
              />
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="perfil-lastName">
                Apellido
              </label>
              <input
                id="perfil-lastName"
                className={styles.fieldInput}
                type="text"
                autoComplete="family-name"
                value={lastName}
                onChange={(e) => {
                  setLastName(e.target.value);
                  setSuccessMsg(null);
                }}
                required
              />
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="perfil-email">
                Email
              </label>
              <input
                id="perfil-email"
                className={styles.fieldInput}
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  setSuccessMsg(null);
                  setErrorMsg(null);
                }}
                required
              />
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="perfil-phone">
                Teléfono <span style={{ fontWeight: 400, opacity: 0.6 }}>(opcional)</span>
              </label>
              <input
                id="perfil-phone"
                className={styles.fieldInput}
                type="tel"
                autoComplete="tel"
                placeholder="+34 6XX XXX XXX"
                value={phone}
                onChange={(e) => {
                  setPhone(e.target.value);
                  setSuccessMsg(null);
                }}
              />
            </div>

            <button
              type="submit"
              className={styles.submitBtn}
              disabled={submitting}
            >
              {submitting ? 'Guardando…' : 'Guardar cambios'}
            </button>
          </form>
        </>
      )}
    </div>
  );
}
