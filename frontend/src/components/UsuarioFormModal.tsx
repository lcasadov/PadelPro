// UsuarioFormModal — formulario modal reutilizable para alta y edición de
// usuarios desde el panel admin (change usuarios-alta-edicion-email, D6/D7).
//
// - Alta (mode="create"): pide nombre, apellidos, email, teléfono y ROL.
//   NO hay campo de contraseña — la genera el backend (D2/D3).
// - Edición (mode="edit"): precarga los datos de contacto; NO ofrece cambiar
//   el rol ni el estado (D7, evita escalada de privilegios).
//
// La accesibilidad se cubre con role="dialog", aria-modal y cierre por Escape.
import { FormEvent, useEffect, useRef, useState } from 'react';
import type { AdminUsuario } from '../services/adminUsuariosApi';
import styles from './UsuarioFormModal.module.css';

export interface UsuarioFormValues {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  role: string;
}

interface Props {
  mode: 'create' | 'edit';
  initial?: AdminUsuario | null;
  submitting?: boolean;
  errorMsg?: string | null;
  onSubmit: (values: UsuarioFormValues) => void;
  onClose: () => void;
}

const ROLES = [
  { value: 'USER', label: 'Usuario' },
  { value: 'ADMIN', label: 'Administrador' },
];

// Validación de formato de email en cliente: evita disparar la request cuando
// el email está mal formado (el backend también lo rechaza con 400
// VALIDATION_ERROR, ver change usuarios-alta-edicion-email #179). Regex simple
// y coherente con el @Email de Bean Validation: algo@algo.dominio, sin espacios.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function UsuarioFormModal({
  mode,
  initial,
  submitting = false,
  errorMsg,
  onSubmit,
  onClose,
}: Props) {
  const [firstName, setFirstName] = useState(initial?.firstName ?? '');
  const [lastName, setLastName] = useState(initial?.lastName ?? '');
  const [email, setEmail] = useState(initial?.email ?? '');
  const [phone, setPhone] = useState(initial?.phone ?? '');
  const [role, setRole] = useState(initial?.role ?? 'USER');
  const [emailError, setEmailError] = useState<string | null>(null);

  const firstFieldRef = useRef<HTMLInputElement>(null);

  const title = mode === 'create' ? 'Dar de alta usuario' : 'Editar usuario';

  useEffect(() => {
    firstFieldRef.current?.focus();
  }, []);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!EMAIL_RE.test(email.trim())) {
      setEmailError('El email no tiene un formato válido.');
      return;
    }
    setEmailError(null);
    onSubmit({ firstName, lastName, email, phone, role });
  }

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitle}>{title}</h2>
          <button
            type="button"
            className={styles.closeBtn}
            onClick={onClose}
            aria-label="Cerrar"
          >
            ×
          </button>
        </div>

        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          <div className="p-input-group">
            <label className="p-input-label" htmlFor="usuario-nombre">
              Nombre
            </label>
            <input
              id="usuario-nombre"
              ref={firstFieldRef}
              className="p-input"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              required
            />
          </div>

          <div className="p-input-group">
            <label className="p-input-label" htmlFor="usuario-apellidos">
              Apellidos
            </label>
            <input
              id="usuario-apellidos"
              className="p-input"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              required
            />
          </div>

          <div className="p-input-group">
            <label className="p-input-label" htmlFor="usuario-email">
              Email
            </label>
            <input
              id="usuario-email"
              className="p-input"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (emailError) setEmailError(null);
              }}
              aria-invalid={emailError ? true : undefined}
              aria-describedby={emailError ? 'usuario-email-error' : undefined}
              required
            />
            {emailError && (
              <p id="usuario-email-error" className="p-error" role="alert">
                {emailError}
              </p>
            )}
          </div>

          <div className="p-input-group">
            <label className="p-input-label" htmlFor="usuario-phone">
              Teléfono
            </label>
            <input
              id="usuario-phone"
              className="p-input"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </div>

          {/* Rol solo en el alta — en la edición queda fuera (D7). */}
          {mode === 'create' && (
            <div className="p-input-group">
              <label className="p-input-label" htmlFor="usuario-rol">
                Rol
              </label>
              <select
                id="usuario-rol"
                className="p-input"
                value={role}
                onChange={(e) => setRole(e.target.value)}
              >
                {ROLES.map((r) => (
                  <option key={r.value} value={r.value}>
                    {r.label}
                  </option>
                ))}
              </select>
            </div>
          )}

          {mode === 'create' && (
            <p className={styles.hint}>
              La contraseña se genera automáticamente y se envía al usuario por email.
            </p>
          )}

          {errorMsg && (
            <p className="p-error" role="alert">
              {errorMsg}
            </p>
          )}

          <div className={styles.actions}>
            <button
              type="button"
              className="p-btn p-btn-outline"
              onClick={onClose}
              disabled={submitting}
            >
              Cancelar
            </button>
            <button type="submit" className="p-btn p-btn-primary" disabled={submitting}>
              {submitting
                ? 'Guardando…'
                : mode === 'create'
                  ? 'Dar de alta'
                  : 'Guardar cambios'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
