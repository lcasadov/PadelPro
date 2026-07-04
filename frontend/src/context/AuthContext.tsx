// T-029 — AuthContext
// El access token JWT vive SOLO en memoria React.
// NUNCA se persiste en localStorage ni sessionStorage (RN-AUTH-09).
//
// acceso-cuenta-prod: además del token, la sesión guarda el `role` (para el
// guard de rol AdminRoute — D6) y el flag `mustChangePassword` (D9), ambos
// devueltos por POST /api/auth/login. Nada de esto se persiste.
import React, { createContext, useContext, useState } from 'react';
import { getMeApi } from '../services/usuariosApi';

export interface Session {
  accessToken: string;
  role: string | null;
  mustChangePassword: boolean;
}

interface AuthContextValue {
  accessToken: string | null;
  role: string | null;
  mustChangePassword: boolean;
  /**
   * Id numérico del usuario autenticado (reservas-ui-jugador D8). Se carga de
   * forma lazy vía `loadUserId()` al entrar en el área de reservas y sirve para
   * comparar `me.id == ownerId` y decidir el botón cancelar. `null` hasta que se
   * carga o tras logout.
   */
  userId: number | null;
  isAuthenticated: boolean;
  /** Legacy setter — usado por HomePage/logout y tests existentes. */
  setAccessToken: (token: string | null) => void;
  /** Establece la sesión completa (token + role + mustChangePassword) tras el login. */
  setSession: (session: Session) => void;
  /** Limpia el flag de cambio forzado tras un cambio de contraseña correcto. */
  clearMustChangePassword: () => void;
  /**
   * Carga el id del usuario vía getMeApi(token) una sola vez (idempotente).
   * No-op si ya está cargado o si no hay token en sesión.
   */
  loadUserId: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [role, setRole] = useState<string | null>(null);
  const [mustChangePassword, setMustChangePassword] = useState(false);
  const [userId, setUserId] = useState<number | null>(null);

  function setAccessToken(token: string | null) {
    setAccessTokenState(token);
    if (token === null) {
      // Logout — limpiar toda la sesión (incluido el id cacheado, D8).
      setRole(null);
      setMustChangePassword(false);
      setUserId(null);
    }
  }

  function setSession(session: Session) {
    setAccessTokenState(session.accessToken);
    setRole(session.role);
    setMustChangePassword(session.mustChangePassword);
    // Sesión nueva: el id se recargará de forma lazy al entrar en reservas.
    setUserId(null);
  }

  function clearMustChangePassword() {
    setMustChangePassword(false);
  }

  async function loadUserId() {
    // Idempotente: solo pide al backend si hay token y aún no está cargado (D8).
    if (!accessToken || userId !== null) return;
    const me = await getMeApi(accessToken);
    setUserId(me.id);
  }

  return (
    <AuthContext.Provider
      value={{
        accessToken,
        role,
        mustChangePassword,
        userId,
        isAuthenticated: !!accessToken,
        setAccessToken,
        setSession,
        clearMustChangePassword,
        loadUserId,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
