// reservas-ui-jugador-fixes (Grupo 3, D4/D5) — selector de participante adicional.
// Conmutador "socio registrado / invitado externo":
//   - socio: buscador que consume GET /api/usuarios/buscar y fija `userId`.
//   - externo: nombre (+ teléfono opcional) que fijan `externalName`/`externalPhone`.
// El estado guarda ambos lados pero el payload se construye XOR en el padre
// (ConfirmarReservaPage): exactamente uno por participante, acorde al backend.
//
// El modo por defecto es "externo" para mantener el flujo simple del jugador que
// invita a alguien sin cuenta; cambiar de modo limpia los datos del otro lado para
// que nunca queden a la vez `userId` y `externalName` (ambigüedad).
import { useEffect, useRef, useState } from 'react';
import { buscarUsuariosApi, UsuarioBusqueda } from '../services/usuariosApi';
import { ParticipanteInput, TipoParticipante } from './participanteModel';
import styles from './ParticipanteSelector.module.css';

/** Longitud mínima del término antes de consultar el buscador de socios (D5). */
const MIN_QUERY = 2;

interface Props {
  index: number;
  value: ParticipanteInput;
  token: string;
  onChange: (next: ParticipanteInput) => void;
  onRemove: () => void;
}

export function ParticipanteSelector({ index, value, token, onChange, onRemove }: Props) {
  const [query, setQuery] = useState('');
  const [resultados, setResultados] = useState<UsuarioBusqueda[]>([]);
  const [buscando, setBuscando] = useState(false);
  // Evita aplicar resultados de una búsqueda obsoleta (última query gana).
  const queryRef = useRef('');

  useEffect(() => {
    queryRef.current = query;
    const term = query.trim();
    if (value.tipo !== 'socio' || term.length < MIN_QUERY) {
      setResultados([]);
      setBuscando(false);
      return;
    }
    setBuscando(true);
    const handle = setTimeout(async () => {
      try {
        const res = await buscarUsuariosApi(token, term);
        if (queryRef.current === query) setResultados(res);
      } catch {
        if (queryRef.current === query) setResultados([]);
      } finally {
        if (queryRef.current === query) setBuscando(false);
      }
    }, 200);
    return () => clearTimeout(handle);
  }, [query, token, value.tipo]);

  function setTipo(tipo: TipoParticipante) {
    if (tipo === value.tipo) return;
    setQuery('');
    setResultados([]);
    // Limpiar el lado contrario evita la ambigüedad (XOR).
    onChange(
      tipo === 'socio'
        ? { ...value, tipo, externalName: '', externalPhone: '' }
        : { ...value, tipo, userId: null, socioNombre: '' }
    );
  }

  function seleccionarSocio(u: UsuarioBusqueda) {
    onChange({ ...value, userId: u.id, socioNombre: u.nombre });
    setQuery('');
    setResultados([]);
  }

  function limpiarSocio() {
    onChange({ ...value, userId: null, socioNombre: '' });
  }

  const numero = index + 1;

  return (
    <div className={styles.row}>
      <div className={styles.rowHead}>
        <span className={styles.rowTitle}>Compañero {numero}</span>
        <button
          type="button"
          className={styles.removeBtn}
          onClick={onRemove}
          aria-label={`Quitar compañero ${numero}`}
        >
          ×
        </button>
      </div>

      {/* Conmutador de tipo (D4) */}
      <div className={styles.toggle} role="group" aria-label={`Tipo de compañero ${numero}`}>
        <button
          type="button"
          className={`${styles.toggleBtn} ${value.tipo === 'socio' ? styles.toggleOn : ''}`}
          aria-pressed={value.tipo === 'socio'}
          onClick={() => setTipo('socio')}
        >
          Socio registrado
        </button>
        <button
          type="button"
          className={`${styles.toggleBtn} ${value.tipo === 'externo' ? styles.toggleOn : ''}`}
          aria-pressed={value.tipo === 'externo'}
          onClick={() => setTipo('externo')}
        >
          Invitado externo
        </button>
      </div>

      {value.tipo === 'socio' ? (
        <div className={styles.socioBlock}>
          {value.userId != null ? (
            <div className={styles.socioSelected}>
              <span className={styles.socioName}>{value.socioNombre}</span>
              <button type="button" className={styles.linkBtn} onClick={limpiarSocio}>
                Cambiar
              </button>
            </div>
          ) : (
            <>
              <label className={styles.fieldLabel} htmlFor={`socio-buscar-${index}`}>
                Buscar socio {numero}
              </label>
              <input
                id={`socio-buscar-${index}`}
                className={styles.textInput}
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Nombre o email del socio"
                autoComplete="off"
                role="combobox"
                aria-expanded={resultados.length > 0}
                aria-controls={`socio-resultados-${index}`}
              />
              {buscando && <span className={styles.hint}>Buscando…</span>}
              {!buscando && query.trim().length >= MIN_QUERY && resultados.length === 0 && (
                <span className={styles.hint}>Sin coincidencias</span>
              )}
              {resultados.length > 0 && (
                <ul id={`socio-resultados-${index}`} className={styles.resultList} role="listbox">
                  {resultados.map((u) => (
                    <li key={u.id}>
                      <button
                        type="button"
                        className={styles.resultBtn}
                        onClick={() => seleccionarSocio(u)}
                      >
                        {u.nombre}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      ) : (
        <div className={styles.externoBlock}>
          <label className={styles.fieldLabel} htmlFor={`externo-nombre-${index}`}>
            Nombre del compañero {numero}
          </label>
          <input
            id={`externo-nombre-${index}`}
            className={styles.textInput}
            type="text"
            value={value.externalName}
            onChange={(e) => onChange({ ...value, externalName: e.target.value })}
            placeholder="Nombre y apellido"
          />
          <label className={styles.fieldLabel} htmlFor={`externo-tel-${index}`}>
            Teléfono del compañero {numero} (opcional)
          </label>
          <input
            id={`externo-tel-${index}`}
            className={styles.textInput}
            type="tel"
            value={value.externalPhone}
            onChange={(e) => onChange({ ...value, externalPhone: e.target.value })}
            placeholder="Teléfono"
          />
        </div>
      )}
    </div>
  );
}
