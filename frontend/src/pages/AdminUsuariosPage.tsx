// 6.3 + 6.4 — AdminUsuariosPage — panel de administración de usuarios (D7).
// Lista usuarios, filtra por estado, aprueba pendientes, activa/desactiva y
// resetea contraseñas (la temporal se muestra una sola vez — D3/D4).
// Autorización real en backend (/api/admin/** = ROLE_ADMIN); el guard AdminRoute
// es defensa en profundidad (D6).
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  listUsuariosApi,
  aprobarUsuarioApi,
  updateUsuarioEstadoApi,
  desactivarUsuarioApi,
  resetPasswordApi,
  AdminUsuario,
} from '../services/adminUsuariosApi';
import './pages.css';
import styles from './AdminUsuariosPage.module.css';

const STATUS_FILTERS = [
  { value: '', label: 'Todos' },
  { value: 'PENDING', label: 'Pendientes' },
  { value: 'ACTIVE', label: 'Activos' },
  { value: 'INACTIVE', label: 'Inactivos' },
];

export function AdminUsuariosPage() {
  const { accessToken } = useAuth();

  const [usuarios, setUsuarios] = useState<AdminUsuario[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  // Contraseña temporal mostrada una sola vez tras un reset (D4).
  const [tempReset, setTempReset] = useState<{ email: string; password: string } | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const page = await listUsuariosApi(accessToken, {
        status: statusFilter || undefined,
        page: 0,
        size: 50,
      });
      setUsuarios(page.content);
    } catch {
      setErrorMsg('No se pudo cargar la lista de usuarios. Inténtalo de nuevo.');
    } finally {
      setLoading(false);
    }
  }, [accessToken, statusFilter]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleAprobar(id: number) {
    if (!accessToken) return;
    setBusyId(id);
    setErrorMsg(null);
    try {
      await aprobarUsuarioApi(accessToken, id);
      await load();
    } catch {
      setErrorMsg('No se pudo aprobar el usuario.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleDesactivar(id: number) {
    if (!accessToken) return;
    setBusyId(id);
    setErrorMsg(null);
    try {
      await desactivarUsuarioApi(accessToken, id);
      await load();
    } catch {
      setErrorMsg('No se pudo desactivar el usuario.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleActivar(id: number) {
    if (!accessToken) return;
    setBusyId(id);
    setErrorMsg(null);
    try {
      await updateUsuarioEstadoApi(accessToken, id, 'ACTIVE');
      await load();
    } catch {
      setErrorMsg('No se pudo activar el usuario.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleReset(usuario: AdminUsuario) {
    if (!accessToken) return;
    setBusyId(usuario.id);
    setErrorMsg(null);
    try {
      const res = await resetPasswordApi(accessToken, usuario.id);
      setTempReset({ email: usuario.email, password: res.temporary_password });
    } catch {
      setErrorMsg('No se pudo restablecer la contraseña.');
    } finally {
      setBusyId(null);
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
        <span className={styles.eyebrow}>Gestión de acceso</span>
        <h1>
          Usuarios<br />
          <em>del club.</em>
        </h1>
      </div>

      <div className={styles.filterRow}>
        <label className={styles.filterLabel} htmlFor="admin-status-filter">
          Estado
        </label>
        <select
          id="admin-status-filter"
          className={styles.select}
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          {STATUS_FILTERS.map((f) => (
            <option key={f.value || 'all'} value={f.value}>
              {f.label}
            </option>
          ))}
        </select>
      </div>

      {errorMsg && (
        <p className="p-error" role="alert">
          {errorMsg}
        </p>
      )}

      {/* Contraseña temporal — se muestra una sola vez (D4) */}
      {tempReset && (
        <div className={styles.tempReset} role="status">
          <p className={styles.tempTitle}>Contraseña temporal generada</p>
          <p className={styles.tempFor}>Para: {tempReset.email}</p>
          <code className={styles.tempCode}>{tempReset.password}</code>
          <p className={styles.tempWarning}>
            Se muestra una sola vez. Comunícala al usuario de forma segura; deberá
            cambiarla al iniciar sesión.
          </p>
          <button
            type="button"
            className="p-btn p-btn-outline"
            onClick={() => setTempReset(null)}
          >
            Entendido, ocultar
          </button>
        </div>
      )}

      {loading ? (
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando usuarios" />
        </div>
      ) : usuarios.length === 0 ? (
        <p className={styles.empty}>No hay usuarios para el filtro seleccionado.</p>
      ) : (
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">Usuario</th>
              <th scope="col">Estado</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {usuarios.map((u) => (
              <tr key={u.id}>
                <td>
                  <div className={styles.userName}>
                    {u.firstName} {u.lastName}
                  </div>
                  <div className={styles.userEmail}>{u.email}</div>
                </td>
                <td>
                  <span className={`${styles.statusPill} ${styles[`status_${u.status}`] ?? ''}`}>
                    {u.status}
                  </span>
                </td>
                <td>
                  <div className={styles.actions}>
                    {u.status === 'PENDING' && (
                      <button
                        type="button"
                        className={styles.actionBtn}
                        onClick={() => handleAprobar(u.id)}
                        disabled={busyId === u.id}
                      >
                        Aprobar
                      </button>
                    )}
                    {u.status === 'INACTIVE' && (
                      <button
                        type="button"
                        className={styles.actionBtn}
                        onClick={() => handleActivar(u.id)}
                        disabled={busyId === u.id}
                      >
                        Activar
                      </button>
                    )}
                    {u.status !== 'INACTIVE' && (
                      <button
                        type="button"
                        className={`${styles.actionBtn} ${styles.actionDanger}`}
                        onClick={() => handleDesactivar(u.id)}
                        disabled={busyId === u.id}
                      >
                        Desactivar
                      </button>
                    )}
                    <button
                      type="button"
                      className={styles.actionBtn}
                      onClick={() => handleReset(u)}
                      disabled={busyId === u.id}
                    >
                      Restablecer contraseña
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
