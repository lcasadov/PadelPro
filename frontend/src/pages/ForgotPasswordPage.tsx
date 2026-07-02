// 7.1 — ForgotPasswordPage (D3): recuperación gobernada por el admin.
// No hay reset self-service por email en esta fase; se indica al usuario que
// contacte con el administrador del club.
import { Link } from 'react-router-dom';
import './pages.css';
import styles from './ForgotPasswordPage.module.css';

export function ForgotPasswordPage() {
  return (
    <div className={styles.page}>
      <div className={styles.logo}>
        <span className="p-dot" />
        PadelPro
      </div>

      <div className={styles.content}>
        <span className={styles.eyebrow}>Recuperar acceso</span>
        <h1 className={styles.headline}>
          ¿Olvidaste tu<br />
          <em>contraseña?</em>
        </h1>
        <p className={styles.body}>
          El acceso al club se gestiona de forma controlada. Para restablecer tu
          contraseña, contacta con el <strong>administrador del club</strong>: él
          generará una contraseña temporal que deberás cambiar en tu próximo inicio
          de sesión.
        </p>
      </div>

      <div className={styles.footer}>
        <Link to="/login" className="p-btn p-btn-primary">
          Volver al inicio de sesión <span className="p-arrow" aria-hidden="true">→</span>
        </Link>
      </div>
    </div>
  );
}
