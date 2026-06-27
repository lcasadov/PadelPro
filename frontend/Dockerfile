# Multi-stage build: compila el bundle y sirve el BUILD ESTÁTICO (producción).
# D6 (design.md): en producción NO se usa `vite dev`. Se sirve el contenido de
# `dist/` con `vite preview` (sin dependencias nuevas; `vite` ya está en devDeps).
# Para desarrollo con HMR, los desarrolladores usan `npm run dev` en local.

FROM node:20-alpine AS builder

WORKDIR /app

# Instalar dependencias (capa cacheable)
COPY package*.json ./
RUN npm ci --quiet

# Copiar fuentes y construir el bundle estático
COPY . .
RUN npm run build

# ---------- Runtime: sirve el build estático ----------
FROM node:20-alpine

WORKDIR /app

# Crear usuario no-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Solo lo necesario para servir el bundle: dist/, vite y su config
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/vite.config.ts ./vite.config.ts

RUN chown -R appuser:appgroup /app

EXPOSE 5173

USER appuser

# `vite preview` sirve el contenido estático de dist/ (no es el dev server).
# Se fuerza host 0.0.0.0 y puerto 5173 para mantener paridad con el compose.
CMD ["npx", "vite", "preview", "--host", "0.0.0.0", "--port", "5173"]
