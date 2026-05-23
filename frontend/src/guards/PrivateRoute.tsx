// T-030 — PrivateRoute: guard para rutas que requieren autenticación.
// Si el usuario no está autenticado, redirige a /login.
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export function PrivateRoute() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}
