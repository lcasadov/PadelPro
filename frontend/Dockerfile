# Multi-stage build: build + serve
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --quiet

# Copy source
COPY . .

# Build
RUN npm run build

# Runtime stage
FROM node:20-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Install serve to run the app in dev mode (with HMR support via proxy)
RUN npm install -g vite

# Copy everything (for Vite dev server with hot reload)
COPY . .

# Install dependencies again for runtime
RUN npm ci --quiet

# Set ownership to non-root user
RUN chown -R appuser:appgroup /app

EXPOSE 5173

# Run as non-root user
USER appuser

# Run Vite dev server (permite hot reload)
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
