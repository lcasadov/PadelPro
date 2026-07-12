// 7.2 / 7.3 — VincularTelegramPage
// Ref visual: docs/ux/mockups/15-vincular-telegram.html
//
// Flujo REAL (spec Requirement 1, backend TelegramWebhookService):
//   1. Al montar, PATCH /api/usuarios/me {telegramAction:'LINK'} genera el OTP y
//      devuelve { instructions, otpCode, expiresAt }.
//   2. La pantalla MUESTRA el `otpCode` (monospace) + el deep link al bot. El
//      usuario envía `/vincular <otpCode>` al bot; el WEBHOOK vincula la cuenta.
//   3. "Ya lo envié — comprobar vinculación" RE-CONSULTA GET /api/usuarios/me:
//      si `telegramLinked === true` → éxito; si no → aviso para reintentar.
//
// El código sí se muestra (es lo que el usuario debe enviar al bot). RN-RGPD-04
// se cumple en backend: nunca se loggea ni persiste en claro (solo SHA-256).
import { useState, useEffect } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { iniciarVinculacionTelegramApi } from '../services/telegramApi';
import { getMeApi } from '../services/usuariosApi';
import {
  botDeepLink,
  DEFAULT_BOT_USERNAME,
  OTP_TTL_SECONDS,
} from './telegramPaths';
import './pages.css';
import styles from './VincularTelegramPage.module.css';

/** Formatea segundos restantes como M:SS. */
function formatMMSS(totalSeconds: number): string {
  const s = Math.max(0, totalSeconds);
  const mins = Math.floor(s / 60);
  const secs = s % 60;
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

export function VincularTelegramPage() {
  const { accessToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [otpCode, setOtpCode] = useState<string | null>(null);
  const [instructions, setInstructions] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [remaining, setRemaining] = useState<number | null>(null);

  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<'idle' | 'notyet'>('idle');
  const [linked, setLinked] = useState(false);

  // Inicia la vinculación al montar. Extraída para reutilizarse al "Solicitar
  // código nuevo". Los hooks se declaran antes del guard (reglas de React).
  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;
    setLoading(true);
    setErrorMsg(null);
    iniciarVinculacionTelegramApi(accessToken)
      .then((res) => {
        if (cancelled) return;
        setOtpCode(res.otpCode ?? null);
        setInstructions(res.instructions ?? null);
        const ts = res.expiresAt ? Date.parse(res.expiresAt) : Date.now() + OTP_TTL_SECONDS * 1000;
        setExpiresAt(Number.isNaN(ts) ? Date.now() + OTP_TTL_SECONDS * 1000 : ts);
      })
      .catch(() => {
        if (!cancelled) setErrorMsg('No se pudo iniciar la vinculación. Inténtalo de nuevo.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken]);

  // Contador regresivo desde `expiresAt`.
  useEffect(() => {
    if (expiresAt == null) return;
    const tick = () => setRemaining(Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000)));
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [expiresAt]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const expired = remaining !== null && remaining <= 0;

  async function requestNewCode() {
    if (!accessToken) return;
    setErrorMsg(null);
    setCheckResult('idle');
    setLoading(true);
    try {
      const res = await iniciarVinculacionTelegramApi(accessToken);
      setOtpCode(res.otpCode ?? null);
      setInstructions(res.instructions ?? null);
      const ts = res.expiresAt ? Date.parse(res.expiresAt) : Date.now() + OTP_TTL_SECONDS * 1000;
      setExpiresAt(Number.isNaN(ts) ? Date.now() + OTP_TTL_SECONDS * 1000 : ts);
    } catch {
      setErrorMsg('No se pudo solicitar un código nuevo. Inténtalo de nuevo.');
    } finally {
      setLoading(false);
    }
  }

  async function comprobarVinculacion() {
    if (!accessToken) return;
    setChecking(true);
    setCheckResult('idle');
    try {
      const me = await getMeApi(accessToken);
      if (me.telegramLinked) {
        setLinked(true);
      } else {
        setCheckResult('notyet');
      }
    } catch {
      setErrorMsg('No se pudo comprobar la vinculación. Inténtalo de nuevo.');
    } finally {
      setChecking(false);
    }
  }

  // ── Vista de éxito ──────────────────────────────────────────────────────────
  if (linked) {
    return (
      <div className={styles.page}>
        <div className={styles.successView} role="status">
          <div className={styles.successIcon} aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </div>
          <h1>Telegram vinculado</h1>
          <p>Tu cuenta ya está conectada. Recibirás avisos y podrás usar el OTP como segundo factor.</p>
          <button
            type="button"
            className="p-btn p-btn-primary"
            onClick={() => navigate('/perfil')}
          >
            Volver a mi perfil
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.topbar}>
        <button
          type="button"
          className={styles.backBtn}
          aria-label="Volver a mi perfil"
          onClick={() => navigate('/perfil')}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
      </header>

      <div className={styles.hero}>
        <div className={styles.tgIcon} aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="white">
            <path d="M21.5 4.5l-3 15s-.5 1-1.5 1c-.5 0-2-1-2-1l-7-5-3-1s-1-.5-1-1.5 1-1.5 1-1.5l16-5s.5-.5 1-.5c.5 0 .5.5.5.5z" />
          </svg>
        </div>
        <h1>
          Activa <em>Telegram</em> en tu cuenta
        </h1>
        <p>
          Recibe avisos de tu próxima reserva y usa el bot como segundo factor de
          autenticación.
        </p>
      </div>

      {errorMsg && (
        <p className="p-error" role="alert">
          {errorMsg}
        </p>
      )}

      <ol className={styles.steps}>
        <li className={styles.step}>
          <span className={styles.stepNum} aria-hidden="true">1</span>
          <div className={styles.stepText}>
            <div className={styles.stepTitle}>Abre el bot en Telegram</div>
            <div className={styles.stepDesc}>Pulsa el botón de abajo o búscalo en Telegram.</div>
            <span className={styles.botName}>@{DEFAULT_BOT_USERNAME}</span>
          </div>
        </li>
        <li className={styles.step}>
          <span className={styles.stepNum} aria-hidden="true">2</span>
          <div className={styles.stepText}>
            <div className={styles.stepTitle}>Envía este comando al bot</div>
            <div className={styles.stepDesc}>
              {instructions ?? 'Copia el código y envíaselo al bot para vincular tu cuenta.'}
            </div>

            {loading && !otpCode ? (
              <div className={styles.codeCard} aria-busy="true">
                <span className={styles.codeLabel}>Generando código…</span>
              </div>
            ) : otpCode ? (
              <div className={styles.codeCard}>
                <span className={styles.codeCommand}>
                  /vincular <span className={styles.codeValue}>{otpCode}</span>
                </span>
                {!expired && remaining !== null && (
                  <span className={styles.codeTimer}>
                    Caduca en <span className={styles.codeTimerNum}>{formatMMSS(remaining)}</span>
                  </span>
                )}
              </div>
            ) : null}

            {expired && (
              <p className={styles.warning} role="alert">
                El código ha caducado. Solicita uno nuevo para continuar.
              </p>
            )}
          </div>
        </li>
        <li className={styles.step}>
          <span className={styles.stepNum} aria-hidden="true">3</span>
          <div className={styles.stepText}>
            <div className={styles.stepTitle}>Comprueba la vinculación</div>
            <div className={styles.stepDesc}>
              Cuando el bot te confirme, pulsa el botón para comprobar tu cuenta.
            </div>
          </div>
        </li>
      </ol>

      {checkResult === 'notyet' && (
        <p className={styles.warning} role="alert">
          Aún no detectamos la vinculación. Asegúrate de haber enviado el comando al bot y reinténtalo.
        </p>
      )}

      <div className={styles.actions}>
        <a
          className={`p-btn p-btn-outline ${styles.openBtn}`}
          href={botDeepLink(DEFAULT_BOT_USERNAME)}
          target="_blank"
          rel="noopener noreferrer"
        >
          Abrir Telegram <span className="p-arrow" aria-hidden="true">→</span>
        </a>

        {expired ? (
          <button
            type="button"
            className="p-btn p-btn-primary"
            onClick={requestNewCode}
            disabled={loading}
          >
            {loading ? 'Generando…' : 'Solicitar código nuevo'}
          </button>
        ) : (
          <button
            type="button"
            className="p-btn p-btn-primary"
            onClick={comprobarVinculacion}
            disabled={loading || checking}
          >
            {checking ? 'Comprobando…' : 'Ya lo envié — comprobar vinculación'}
          </button>
        )}
      </div>
    </div>
  );
}
