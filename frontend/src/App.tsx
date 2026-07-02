import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { PrivateRoute } from './guards/PrivateRoute';
import { AdminRoute } from './guards/AdminRoute';
import { SplashPage } from './pages/SplashPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { HomePage } from './pages/HomePage';
import { MiPerfilPage } from './pages/MiPerfilPage';
import { AdminUsuariosPage } from './pages/AdminUsuariosPage';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Rutas públicas */}
          <Route path="/" element={<SplashPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />

          {/* Rutas privadas */}
          <Route element={<PrivateRoute />}>
            <Route path="/home" element={<HomePage />} />
            <Route path="/perfil" element={<MiPerfilPage />} />
            {/* Cambio de contraseña forzado (D9) — accesible a cualquier autenticado */}
            <Route path="/cambiar-password" element={<ChangePasswordPage />} />
          </Route>

          {/* Rutas de administración — guard de rol ADMIN (D6) */}
          <Route element={<AdminRoute />}>
            <Route path="/admin/usuarios" element={<AdminUsuariosPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
