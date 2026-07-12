/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react() as Parameters<typeof defineConfig>[0]['plugins']],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    testTimeout: 15000,
    coverage: {
      provider: 'v8',
      // Gate de cobertura global ≥80% (alineado con el gate JaCoCo 0.80 del backend).
      // Estado actual holgado: ~94% líneas / ~82% ramas / ~84% funcs.
      thresholds: { lines: 80, branches: 80, functions: 80, statements: 80 },
    },
  },
});
