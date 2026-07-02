// T-029 — AuthContext
// El access token JWT vive SOLO en memoria React.
// NUNCA se persiste en localStorage ni sessionStorage (RN-AUTH-09).
//
// acceso-cuenta-prod: además del token, la sesión guarda el `role` (para el
// guard de rol AdminRoute — D6) y el flag `mustChangePassword` (D9), ambos
// devueltos por POST /api/auth/login. Nada de esto se persiste.
import React, { createContext, useContext, useState } from 'react';

export interface Session {
  accessToken: string;
  role: string | null;
  mustChangePassword: boolean;
}

interface AuthContextValue {
  accessToken: string | null;
  role: string | null;
  mustChangePassword: boolean;
  isAuthenticated: boolean;
  /** Legacy setter — usado por HomePage/logout y tests existentes. */
  setAccessToken: (token: string | null) => void;
  /** Establece la sesión completa (token + role + mustChangePassword) tras el login. */
  setSession: (session: Session) => void;
  /** Limpia el flag de cambio forzado tras un cambio de contraseña correcto. */
  clearMustChangePassword: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [role, setRole] = useState<string | null>(null);
  const [mustChangePassword, setMustChangePassword] = useState(false);

  function setAccessToken(token: string | null) {
    setAccessTokenState(token);
    if (token === null) {
      // Logout — limpiar toda la sesión.
      setRole(null);
      setMustChangePassword(false);
    }
  }

  function setSession(session: Session) {
    setAccessTokenState(session.accessToken);
    setRole(session.role);
    setMustChangePassword(session.mustChangePassword);
  }

  function clearMustChangePassword() {
    setMustChangePassword(false);
  }

  return (
    <AuthContext.Provider
      value={{
        accessToken,
        role,
        mustChangePassword,
        isAuthenticated: !!accessToken,
        setAccessToken,
        setSession,
        clearMustChangePassword,
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
