// admin-config-club — ConfiguracionPage (/admin/config, solo ADMIN).
// Lee y edita la configuración global del club: nombre/descripción, precio por hora,
// estado de la pista (ACTIVA/MANTENIMIENTO), aforo, plazo de cancelación, pasarela de
// pago y credenciales de Telegram (bot token + webhook secret + group id).
//
// Los secretos NO se leen del backend (solo un booleano "configurado"): se muestran
// con placeholder y solo se envían en el PATCH si el admin teclea uno nuevo — dejarlos
// en blanco conserva el valor almacenado (semántica parcial). Autorización real en
// backend (/api/admin/** = ROLE_ADMIN); AdminRoute es defensa en profundidad.
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  getSystemConfig,
  updateSystemConfig,
  type PaymentGateway,
  type PistaState,
  type UpdateSystemConfig,
} from '../services/configApi';
import './pages.css';
import styles from './ConfiguracionPage.module.css';

/** Valores del formulario. Los numéricos se manejan como string para editarlos con
 *  comodidad y se parsean al guardar. Los secretos empiezan vacíos (no se leen). */
interface FormState {
  clubName: string;
  clubDescription: string;
  pistaState: PistaState;
  paymentGateway: PaymentGateway;
  maxParticipantsPerPista: string;
  pricePerHour: string;
  cancellationDeadlineHours: string;
  telegramBotToken: string;
  telegramWebhookSecret: string;
  telegramGroupId: string;
  redsysMerchantId: string;
  redsysMerchantKey: string;
}

const EMPTY_SECRETS = {
  telegramBotToken: '',
  telegramWebhookSecret: '',
  telegramGroupId: '',
  redsysMerchantId: '',
  redsysMerchantKey: '',
};

export function ConfiguracionPage() {
  const { accessToken } = useAuth();

  const [form, setForm] = useState<FormState | null>(null);
  const [telegramConfigured, setTelegramConfigured] = useState(false);
  const [redsysConfigured, setRedsysConfigured] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [okMsg, setOkMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const cfg = await getSystemConfig(accessToken);
      setForm({
        clubName: cfg.clubName ?? '',
        clubDescription: cfg.clubDescription ?? '',
        pistaState: cfg.pistaState,
        paymentGateway: cfg.paymentGateway,
        maxParticipantsPerPista: String(cfg.maxParticipantsPerPista),
        pricePerHour: String(cfg.pricePerHour),
        cancellationDeadlineHours: String(cfg.cancellationDeadlineHours),
        ...EMPTY_SECRETS,
      });
      setTelegramConfigured(cfg.telegramBotConfigured);
      setRedsysConfigured(cfg.redsysConfigured);
    } catch {
      setErrorMsg('No se pudo cargar la configuración. Inténtalo de nuevo.');
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    load();
  }, [load]);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));
    setOkMsg(null);
  }

  /** Valida en cliente (espejo del backend) y devuelve un mensaje de error o null. */
  function validate(f: FormState): string | null {
    if (!f.clubName.trim()) return 'El nombre del club es obligatorio.';
    const price = Number(f.pricePerHour);
    if (!Number.isFinite(price) || price <= 0) return 'El precio por hora debe ser mayor que 0.';
    const max = Number(f.maxParticipantsPerPista);
    if (!Number.isInteger(max) || max < 1) return 'El aforo máximo debe ser 1 o más.';
    const deadline = Number(f.cancellationDeadlineHours);
    if (!Number.isInteger(deadline) || deadline < 0)
      return 'El plazo de cancelación debe ser 0 o más horas.';
    if (f.paymentGateway === 'REDSYS' && (!f.redsysMerchantId.trim() || !f.redsysMerchantKey.trim())) {
      return 'Para activar Redsys debes introducir el Merchant ID y la clave.';
    }
    return null;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form || !accessToken) return;
    const validationError = validate(form);
    if (validationError) {
      setErrorMsg(validationError);
      setOkMsg(null);
      return;
    }

    // Campos obligatorios siempre; secretos solo si se han tecleado (dejarlos en
    // blanco conserva el valor almacenado en el backend).
    const body: UpdateSystemConfig = {
      clubName: form.clubName.trim(),
      clubDescription: form.clubDescription.trim() || null,
      pistaState: form.pistaState,
      paymentGateway: form.paymentGateway,
      maxParticipantsPerPista: Number(form.maxParticipantsPerPista),
      pricePerHour: Number(form.pricePerHour),
      cancellationDeadlineHours: Number(form.cancellationDeadlineHours),
    };
    if (form.telegramBotToken.trim()) body.telegramBotToken = form.telegramBotToken.trim();
    if (form.telegramWebhookSecret.trim()) body.telegramWebhookSecret = form.telegramWebhookSecret.trim();
    if (form.telegramGroupId.trim()) body.telegramGroupId = form.telegramGroupId.trim();
    if (form.redsysMerchantId.trim()) body.redsysMerchantId = form.redsysMerchantId.trim();
    if (form.redsysMerchantKey.trim()) body.redsysMerchantKey = form.redsysMerchantKey.trim();

    setSaving(true);
    setErrorMsg(null);
    setOkMsg(null);
    try {
      const cfg = await updateSystemConfig(accessToken, body);
      // Refresca con la respuesta y limpia los campos de secreto.
      setForm({
        clubName: cfg.clubName ?? '',
        clubDescription: cfg.clubDescription ?? '',
        pistaState: cfg.pistaState,
        paymentGateway: cfg.paymentGateway,
        maxParticipantsPerPista: String(cfg.maxParticipantsPerPista),
        pricePerHour: String(cfg.pricePerHour),
        cancellationDeadlineHours: String(cfg.cancellationDeadlineHours),
        ...EMPTY_SECRETS,
      });
      setTelegramConfigured(cfg.telegramBotConfigured);
      setRedsysConfigured(cfg.redsysConfigured);
      setOkMsg('Configuración guardada.');
    } catch {
      setErrorMsg('No se pudo guardar la configuración. Revisa los datos e inténtalo de nuevo.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className={styles.adminPage}>
      <header className={styles.header}>
        <Link to="/home" className="p-logo" aria-label="Volver al inicio">
          <span className="p-dot" />
          PadelPro
        </Link>
        <span className={styles.badge}>Admin</span>
      </header>

      <div className={styles.titleBlock}>
        <span className={styles.eyebrow}>Administración</span>
        <h1>
          Configuración<br />
          <em>del club.</em>
        </h1>
      </div>

      {errorMsg && (
        <p className="p-error" role="alert">
          {errorMsg}
        </p>
      )}
      {okMsg && (
        <p className={styles.ok} role="status">
          {okMsg}
        </p>
      )}

      {loading || !form ? (
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando configuración" />
        </div>
      ) : (
        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          <fieldset className={styles.group}>
            <legend>Club</legend>
            <label className={styles.field}>
              <span>Nombre del club</span>
              <input
                type="text"
                value={form.clubName}
                onChange={(e) => update('clubName', e.target.value)}
                required
              />
            </label>
            <label className={styles.field}>
              <span>Descripción</span>
              <textarea
                value={form.clubDescription}
                onChange={(e) => update('clubDescription', e.target.value)}
                rows={2}
              />
            </label>
          </fieldset>

          <fieldset className={styles.group}>
            <legend>Reservas y precio</legend>
            <label className={styles.field}>
              <span>Precio por hora (€)</span>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={form.pricePerHour}
                onChange={(e) => update('pricePerHour', e.target.value)}
                required
              />
            </label>
            <label className={styles.field}>
              <span>Aforo máximo por pista</span>
              <input
                type="number"
                min="1"
                step="1"
                value={form.maxParticipantsPerPista}
                onChange={(e) => update('maxParticipantsPerPista', e.target.value)}
                required
              />
            </label>
            <label className={styles.field}>
              <span>Plazo de cancelación (horas)</span>
              <input
                type="number"
                min="0"
                step="1"
                value={form.cancellationDeadlineHours}
                onChange={(e) => update('cancellationDeadlineHours', e.target.value)}
                required
              />
            </label>
            <label className={styles.field}>
              <span>Estado de la pista</span>
              <select
                value={form.pistaState}
                onChange={(e) => update('pistaState', e.target.value as PistaState)}
              >
                <option value="ACTIVA">Activa</option>
                <option value="MANTENIMIENTO">Mantenimiento</option>
              </select>
              {form.pistaState === 'MANTENIMIENTO' && (
                <small className={styles.hint}>
                  En mantenimiento, los jugadores no ven ninguna disponibilidad.
                </small>
              )}
            </label>
          </fieldset>

          <fieldset className={styles.group}>
            <legend>Pasarela de pago</legend>
            <label className={styles.field}>
              <span>Pasarela</span>
              <select
                value={form.paymentGateway}
                onChange={(e) => update('paymentGateway', e.target.value as PaymentGateway)}
              >
                <option value="CASH">Efectivo / simulador</option>
                <option value="REDSYS">Redsys</option>
              </select>
            </label>
            {form.paymentGateway === 'REDSYS' && (
              <>
                <p className={styles.hint}>
                  Redsys requiere las credenciales del comercio.
                  {redsysConfigured ? ' Ya hay credenciales guardadas; déjalas en blanco para conservarlas.' : ''}
                </p>
                <label className={styles.field}>
                  <span>Redsys Merchant ID</span>
                  <input
                    type="text"
                    value={form.redsysMerchantId}
                    onChange={(e) => update('redsysMerchantId', e.target.value)}
                    autoComplete="off"
                  />
                </label>
                <label className={styles.field}>
                  <span>Redsys Merchant Key</span>
                  <input
                    type="password"
                    value={form.redsysMerchantKey}
                    onChange={(e) => update('redsysMerchantKey', e.target.value)}
                    autoComplete="off"
                  />
                </label>
              </>
            )}
          </fieldset>

          <fieldset className={styles.group}>
            <legend>Telegram</legend>
            <p className={styles.hint}>
              {telegramConfigured
                ? 'Bot configurado. Deja los campos en blanco para conservar los valores actuales.'
                : 'Bot no configurado. Introduce el token del bot para activar las notificaciones y la vinculación.'}
            </p>
            <label className={styles.field}>
              <span>Bot token</span>
              <input
                type="password"
                value={form.telegramBotToken}
                onChange={(e) => update('telegramBotToken', e.target.value)}
                placeholder={telegramConfigured ? '••••••••' : ''}
                autoComplete="off"
              />
            </label>
            <label className={styles.field}>
              <span>Webhook secret</span>
              <input
                type="password"
                value={form.telegramWebhookSecret}
                onChange={(e) => update('telegramWebhookSecret', e.target.value)}
                placeholder={telegramConfigured ? '••••••••' : ''}
                autoComplete="off"
              />
            </label>
            <label className={styles.field}>
              <span>Group chat id</span>
              <input
                type="text"
                value={form.telegramGroupId}
                onChange={(e) => update('telegramGroupId', e.target.value)}
                placeholder="Déjalo en blanco para conservar el actual"
                autoComplete="off"
              />
            </label>
          </fieldset>

          <button type="submit" className={styles.submitBtn} disabled={saving}>
            {saving ? 'Guardando…' : 'Guardar configuración'}
          </button>
        </form>
      )}
    </div>
  );
}
