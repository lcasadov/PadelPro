// T-144 — HomePage — pantalla principal post-login
// Ref visual: docs/ux/mockups/02-home-jugador.html
// Incluye enlace a /perfil (Mi perfil)
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { reservasPaths } from './reservasPaths';
import './pages.css';
import styles from './HomePage.module.css';

export function HomePage() {
  const { setAccessToken, role } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    setAccessToken(null);
  }

  function handleReservar() {
    navigate(reservasPaths.disponibilidad);
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
        <button
          type="button"
          className={`${styles.action} ${styles.actionPrimary}`}
          onClick={handleReservar}
        >
          <div className={styles.actionIco}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <rect x="3" y="4" width="18" height="18" rx="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
          </div>
          <div className={styles.actionLabel}>Reservar pista</div>
          <div className={styles.actionSub}>Encuentra una franja libre</div>
        </button>

        <Link to={reservasPaths.partidas} className={styles.action}>
          <div className={styles.actionIco}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          </div>
          <div className={styles.actionLabel}>Partidas abiertas</div>
          <div className={styles.actionSub}>Únete a una partida con plazas libres</div>
        </Link>

        <Link to={reservasPaths.mias} className={styles.action}>
          <div className={styles.actionIco}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
          </div>
          <div className={styles.actionLabel}>Mis reservas</div>
          <div className={styles.actionSub}>Consulta y gestiona tus pistas</div>
        </Link>

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

        {/* Entrada de administración — solo visible para ADMIN (6.5, D6) */}
        {role === 'ADMIN' && (
          <Link to="/admin/usuarios" className={styles.action}>
            <div className={styles.actionIco}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </div>
            <div className={styles.actionLabel}>Gestionar usuarios</div>
            <div className={styles.actionSub}>Aprobar, activar y resetear</div>
          </Link>
        )}

        {/* Dashboard del club — solo ADMIN (administracion-club, D5) */}
        {role === 'ADMIN' && (
          <Link to="/admin/dashboard" className={styles.action}>
            <div className={styles.actionIco}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <line x1="18" y1="20" x2="18" y2="10" />
                <line x1="12" y1="20" x2="12" y2="4" />
                <line x1="6" y1="20" x2="6" y2="14" />
              </svg>
            </div>
            <div className={styles.actionLabel}>Dashboard del club</div>
            <div className={styles.actionSub}>Ocupación, ingresos y export</div>
          </Link>
        )}

        {/* Cobros — gestión de pagos, solo ADMIN (pagos-simulador-gestion, D3) */}
        {role === 'ADMIN' && (
          <Link to={reservasPaths.adminPagos} className={styles.action}>
            <div className={styles.actionIco}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <rect x="2" y="5" width="20" height="14" rx="2" />
                <line x1="2" y1="10" x2="22" y2="10" />
              </svg>
            </div>
            <div className={styles.actionLabel}>Cobros</div>
            <div className={styles.actionSub}>Pagos, efectivo y estado</div>
          </Link>
        )}

        {/* Configuración del club — solo ADMIN (admin-config-club) */}
        {role === 'ADMIN' && (
          <Link to="/admin/config" className={styles.action}>
            <div className={styles.actionIco}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <circle cx="12" cy="12" r="3" />
                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
              </svg>
            </div>
            <div className={styles.actionLabel}>Configuración</div>
            <div className={styles.actionSub}>Precio, pista y Telegram</div>
          </Link>
        )}

        {/* Bloqueo de franjas para eventos — solo ADMIN (bloqueos-pista-eventos) */}
        {role === 'ADMIN' && (
          <Link to="/admin/bloqueos" className={styles.action}>
            <div className={styles.actionIco}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <rect x="3" y="4" width="18" height="18" rx="2" />
                <line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
                <line x1="9" y1="15" x2="15" y2="15" />
              </svg>
            </div>
            <div className={styles.actionLabel}>Bloquear franjas</div>
            <div className={styles.actionSub}>Reservar la pista para eventos</div>
          </Link>
        )}
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
