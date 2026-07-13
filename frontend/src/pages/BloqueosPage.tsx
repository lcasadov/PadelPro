// bloqueos-pista-eventos — BloqueosPage (/admin/bloqueos, solo ADMIN).
// El admin elige una fecha y ve la rejilla horaria del día (8:00–22:00) con el estado
// de cada franja: LIBRE (marcable para bloquear), OCUPADA (hay reserva, no marcable) o
// BLOQUEADA (con botón desbloquear). Marca franjas libres + un motivo → "Bloquear".
// El bloqueo es todo-o-nada: si el backend detecta una reserva en conflicto (409),
// se avisa y no se crea ninguna.
// Combina GET /api/reservas/disponibles (franjas libres) + GET /api/admin/bloqueos.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getDisponibilidad } from '../services/reservasApi';
import { getBloqueos, crearBloqueos, eliminarBloqueo, type Bloqueo } from '../services/bloqueosApi';
import './pages.css';
import styles from './BloqueosPage.module.css';

// Franjas horarias del club: inicio de cada tramo de 60 min, 8:00–22:00 (23:00 exclusivo).
const HORAS = Array.from({ length: 15 }, (_, i) => `${String(8 + i).padStart(2, '0')}:00`);

type EstadoFranja = 'LIBRE' | 'OCUPADA' | 'BLOQUEADA';

/** Fecha de hoy YYYY-MM-DD para el valor inicial del selector. */
function hoyISO(): string {
  return new Date().toISOString().slice(0, 10);
}

export function BloqueosPage() {
  const { accessToken } = useAuth();

  const [fecha, setFecha] = useState(hoyISO());
  const [libres, setLibres] = useState<Set<string>>(new Set());
  const [bloqueos, setBloqueos] = useState<Bloqueo[]>([]);
  const [seleccion, setSeleccion] = useState<Set<string>>(new Set());
  const [motivo, setMotivo] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [okMsg, setOkMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setErrorMsg(null);
    setSeleccion(new Set());
    try {
      const [disp, bloqs] = await Promise.all([
        getDisponibilidad(accessToken, fecha),
        getBloqueos(accessToken, fecha),
      ]);
      // Solo las franjas COMPLETAMENTE libres (creable) son bloqueables.
      const libresSet = new Set(
        disp.tramosDisponibles.filter((t) => t.creable).map((t) => t.horaInicio)
      );
      setLibres(libresSet);
      setBloqueos(bloqs);
    } catch {
      setErrorMsg('No se pudo cargar la disponibilidad de la fecha. Inténtalo de nuevo.');
    } finally {
      setLoading(false);
    }
  }, [accessToken, fecha]);

  useEffect(() => {
    load();
  }, [load]);

  const bloqueoPorHora = useMemo(() => {
    const map = new Map<string, Bloqueo>();
    for (const b of bloqueos) map.set(b.hora.slice(0, 5), b);
    return map;
  }, [bloqueos]);

  function estadoDe(hora: string): EstadoFranja {
    if (bloqueoPorHora.has(hora)) return 'BLOQUEADA';
    if (libres.has(hora)) return 'LIBRE';
    return 'OCUPADA';
  }

  function toggle(hora: string) {
    setOkMsg(null);
    setSeleccion((prev) => {
      const next = new Set(prev);
      if (next.has(hora)) next.delete(hora);
      else next.add(hora);
      return next;
    });
  }

  async function handleBloquear() {
    if (!accessToken || seleccion.size === 0) return;
    if (!motivo.trim()) {
      setErrorMsg('Indica un motivo para el bloqueo.');
      return;
    }
    setSaving(true);
    setErrorMsg(null);
    setOkMsg(null);
    try {
      await crearBloqueos(accessToken, {
        fecha,
        horas: Array.from(seleccion).sort(),
        motivo: motivo.trim(),
      });
      setMotivo('');
      setOkMsg('Franjas bloqueadas.');
      await load();
    } catch (err) {
      // Conflicto (409): alguna franja tiene una reserva activa; el backend no bloqueó nada.
      const e = err as { code?: string; details?: string[] };
      if (e.code === 'CONFLICT') {
        const detalle = e.details?.length ? ` (${e.details.join(', ')})` : '';
        setErrorMsg(`No se pudo bloquear: hay una reserva en alguna franja seleccionada${detalle}.`);
      } else {
        setErrorMsg('No se pudieron bloquear las franjas. Inténtalo de nuevo.');
      }
    } finally {
      setSaving(false);
    }
  }

  async function handleDesbloquear(id: number) {
    if (!accessToken) return;
    setErrorMsg(null);
    setOkMsg(null);
    try {
      await eliminarBloqueo(accessToken, id);
      await load();
    } catch {
      setErrorMsg('No se pudo desbloquear la franja. Inténtalo de nuevo.');
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
          Bloqueo<br />
          <em>de franjas.</em>
        </h1>
      </div>

      <label className={styles.fechaField}>
        <span>Fecha</span>
        <input type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
      </label>

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

      {loading ? (
        <div className={styles.loadingWrapper}>
          <div className={styles.spinner} role="status" aria-label="Cargando franjas" />
        </div>
      ) : (
        <>
          <ul className={styles.grid} aria-label="Franjas horarias del día">
            {HORAS.map((hora) => {
              const estado = estadoDe(hora);
              const bloqueo = bloqueoPorHora.get(hora);
              const seleccionada = seleccion.has(hora);
              return (
                <li
                  key={hora}
                  className={`${styles.slot} ${styles[`slot_${estado}`]} ${
                    seleccionada ? styles.slotSelected : ''
                  }`}
                >
                  <span className={styles.slotHora}>{hora}</span>
                  {estado === 'LIBRE' && (
                    <label className={styles.slotCheck}>
                      <input
                        type="checkbox"
                        checked={seleccionada}
                        onChange={() => toggle(hora)}
                        aria-label={`Bloquear ${hora}`}
                      />
                      <span>Libre</span>
                    </label>
                  )}
                  {estado === 'OCUPADA' && <span className={styles.slotTag}>Reservada</span>}
                  {estado === 'BLOQUEADA' && bloqueo && (
                    <div className={styles.slotBloqueada}>
                      <span className={styles.slotTag} title={bloqueo.motivo ?? undefined}>
                        Bloqueada
                      </span>
                      <button
                        type="button"
                        className={styles.unblockBtn}
                        onClick={() => handleDesbloquear(bloqueo.id)}
                      >
                        Desbloquear
                      </button>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>

          <div className={styles.actionBar}>
            <label className={styles.motivoField}>
              <span>Motivo</span>
              <input
                type="text"
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Torneo, mantenimiento, evento…"
              />
            </label>
            <button
              type="button"
              className={styles.blockBtn}
              onClick={handleBloquear}
              disabled={saving || seleccion.size === 0}
            >
              {saving ? 'Bloqueando…' : `Bloquear ${seleccion.size} franja${seleccion.size === 1 ? '' : 's'}`}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
