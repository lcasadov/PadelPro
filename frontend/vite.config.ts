import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  // `vite preview` sirve el build estático en producción (D6). Tiene su propia
  // config de proxy/host independiente del dev server.
  preview: {
    port: 5173,
    host: true,
    // Permite acceso por IP pública o DNS del EC2 (D5: exposición IP:puerto directa).
    allowedHosts: true,
    proxy: {
      '/api': {
        // En el contenedor de producción el backend es accesible vía la red del compose.
        target: 'http://backend:8080',
        changeOrigin: true,
      },
    },
  },
});
