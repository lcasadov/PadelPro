// T-144 — HomePage — pantalla principal post-login
// Ref visual: docs/ux/mockups/02-home-jugador.html
// Incluye enlace a /perfil (Mi perfil)
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './pages.css';
import styles from './HomePage.module.css';

export function HomePage() {
  const { setAccessToken } = useAuth();

  function handleLogout() {
    setAccessToken(null);
  }

  return (
    <div className={styles.homePage}>
      {/* ── Top bar ── */}
      <div className={styles.topBar}>
        <div className="p-logo">
          <span className="p-dot" />
          PadelPro
        </div>
        <Link to="/perfil" className={styles.avatarBtn} aria-label="Mi perfil">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <circle cx="12" cy="8" r="4"/>
            <path d="M4 21v-1a7 7 0 0114 0v1"/>
          </svg>
        </Link>
      </div>

      {/* ── Greeting ── */}
      <div className={styles.greet}>
        <span className={styles.eyebrow}>Bienvenido</span>
        <h1>
          Hola,<br />
          <em>jugador.</em>
        </h1>
      </div>

      {/* ── Quick actions ── */}
      <div className={styles.actions}>
        <div className={`${styles.action} ${styles.actionPrimary}`}>
          <div className={styles.actionIco}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="4" width="18" height="18" rx="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
          </div>
          <div className={styles.actionLabel}>Reservar pista</div>
          <div className={styles.actionSub}>Encuentra una franja libre</div>
        </div>

        <Link to="/perfil" className={styles.action}>
          <div className={styles.actionIco}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="8" r="4"/>
              <path d="M4 21v-1a7 7 0 0114 0v1"/>
            </svg>
          </div>
          <div className={styles.actionLabel}>Mi perfil</div>
          <div className={styles.actionSub}>Datos y configuración</div>
        </Link>
      </div>

      {/* ── Logout ── */}
      <button
        type="button"
        className={`p-btn p-btn-outline ${styles.logoutBtn}`}
        onClick={handleLogout}
      >
        Cerrar sesión
      </button>
    </div>
  );
}
