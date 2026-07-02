// 6.1 — AdminRoute: guard para rutas que requieren rol ADMIN (D6).
// La autorización efectiva la impone el backend (/api/admin/** = ROLE_ADMIN);
// este guard es defensa en profundidad / UX: evita que un USER vea el panel.
// - No autenticado  → /login
// - Autenticado sin rol ADMIN → /home
// - ADMIN → renderiza la ruta anidada
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export function AdminRoute() {
  const { isAuthenticated, role } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (role !== 'ADMIN') {
    return <Navigate to="/home" replace />;
  }
  return <Outlet />;
}
