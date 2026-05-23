// T-026 — SplashPage
// Pantalla de bienvenida: logo, glifo, dos CTAs.
// Si el usuario ya está autenticado, redirige a /home.
// Ref visual: docs/ux/mockups/07-splash.html
import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './pages.css';
import styles from './SplashPage.module.css';

export function SplashPage() {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/home', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  return (
    <div className={styles.splash}>
      <div className={styles.top}>
        <div className="p-logo">
          <span className="p-dot" />
          PadelPro
        </div>
      </div>

      <div className={styles.center}>
        <div className="p-glyph" aria-hidden="true">
          PP
        </div>
      </div>

      <footer className={styles.footer}>
        <p className={styles.tagline}>
          Reserva, <em>juega</em>,<br />
          vuelve.
        </p>
        <nav className={styles.actions} aria-label="Acciones de bienvenida">
          <Link to="/register" className={`p-btn p-btn-primary ${styles.btnRegister}`}>
            Crear cuenta
          </Link>
          <Link to="/login" className={`p-btn p-btn-outline ${styles.btnLogin}`}>
            Ya tengo cuenta
          </Link>
        </nav>
      </footer>
    </div>
  );
}
