// auth-session-refresh (Grupo 2, D3/D4) — puente entre el interceptor axios y
// React (AuthContext + routing).
//
// El módulo `httpClient` no conoce React: expone `registerSessionHandlers` para
// recibir dos callbacks. Este componente, montado DENTRO del Router, los registra:
//   - onTokenRefreshed(token): actualiza el access token en memoria (RN-AUTH-09;
//     no se persiste). Conserva role/mustChangePassword/userId de la sesión.
//   - onSessionExpired(): limpia la sesión y redirige a /login.
// No renderiza nada.
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { registerSessionHandlers } from '../services/httpClient';

export function SessionBridge() {
  const { setAccessToken } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    registerSessionHandlers({
      onTokenRefreshed: (token) => setAccessToken(token),
      onSessionExpired: () => {
        setAccessToken(null);
        navigate('/login', { replace: true });
      },
    });
  }, [setAccessToken, navigate]);

  return null;
}
