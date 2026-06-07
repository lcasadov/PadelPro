# 🚀 PadelPro — Quick Start Guide

## Prerequisites

- **Docker** 20.10+ ([install](https://docs.docker.com/get-docker/))
- **Docker Compose** 2.0+ (usually bundled with Docker Desktop)
- **Git** (to clone the repo)

## Setup in 3 Steps

### 1️⃣ Clone and Configure

```bash
git clone https://github.com/lcasadov/PadelPro.git
cd PadelPro

# Copy environment template
cp .env.example .env

# (Optional) Edit .env to change secrets or database credentials
# By default uses development values — safe for local dev only!
```

### 2️⃣ Build and Start Services

```bash
# Build all services (takes ~2-3 min on first run)
docker-compose build

# Start all services (db, backend, frontend)
docker-compose up -d

# Check logs
docker-compose logs -f backend
docker-compose logs -f frontend
```

### 3️⃣ Access the Application

| Service | URL | Notes |
|---|---|---|
| **Frontend** | http://localhost:5173 | React app with Vite dev server |
| **Backend API** | http://localhost:8080 | Spring Boot REST API |
| **API Docs** | http://localhost:8080/swagger-ui.html | Swagger UI (if enabled) |
| **Database** | localhost:5432 | PostgreSQL (user: `padelpro`, password: `padelpro_dev`) |

---

## Common Commands

```bash
# View logs
docker-compose logs -f                    # All services
docker-compose logs -f backend            # Backend only
docker-compose logs -f frontend           # Frontend only
docker-compose logs -f db                 # Database only

# Stop services
docker-compose down                       # Stop all (keep volumes)
docker-compose down -v                    # Stop all + remove volumes

# Rebuild after code changes
docker-compose build --no-cache backend   # Rebuild backend only
docker-compose up -d backend              # Restart backend

# Interactive access
docker-compose exec backend bash          # Shell into backend container
docker-compose exec db psql -U padelpro   # Connect to database

# View service status
docker-compose ps                         # Show running containers
docker-compose stats                      # CPU/memory usage
```

---

## Troubleshooting

### Port already in use

```bash
# Find process using port 5173 or 8080
lsof -i :5173    # macOS/Linux
netstat -ano | findstr :5173  # Windows

# Kill process or use different ports in docker-compose.yml
```

### Database connection failed

```bash
# Check database is running and healthy
docker-compose ps db

# Wait for health check to pass
docker-compose logs db

# Reset database
docker-compose down -v
docker-compose up -d db
docker-compose up -d backend  # Will run migrations
```

### Frontend can't reach backend

```bash
# Check backend is running
curl http://localhost:8080/actuator/health

# Verify VITE_API_BASE_URL in .env points to correct backend URL
# Inside containers, use http://backend:8080 (not localhost)
```

### Rebuild everything from scratch

```bash
docker-compose down -v
docker-compose build --no-cache
docker-compose up -d
```

---

## Development Workflow

### Backend Changes

1. Edit code in `backend/src/...`
2. Rebuild: `docker-compose build --no-cache backend`
3. Restart: `docker-compose up -d backend`
4. View logs: `docker-compose logs -f backend`

### Frontend Changes

1. Edit code in `frontend/src/...`
2. Vite dev server auto-reloads (hot module replacement)
3. Refresh browser at http://localhost:5173

### Database Schema Changes

1. Create new migration in `backend/src/main/resources/db/migration/`
2. Follow naming: `VN__description.sql` (e.g., `V6__add_new_table.sql`)
3. Restart backend: `docker-compose up -d backend`
4. Flyway runs migrations automatically

---

## Environment Variables Explained

| Variable | Purpose | Example | Required |
|---|---|---|---|
| `JWT_SECRET` | Signing key for JWT tokens | `dev-secret-...` | ✅ Yes |
| `JWT_EXPIRATION` | Token lifetime (seconds) | `3600` | ✅ Yes |
| `ENCRYPTION_KEY` | AES-256-GCM for secrets | `dev-encryption-...` | ⚠️ Future (Wave 1) |
| `POSTGRES_PASSWORD` | Database password | `padelpro_dev` | ✅ Yes |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `prod` | ⚠️ Or use `dev` |
| `VITE_API_BASE_URL` | Frontend → Backend URL | `http://backend:8080` | ✅ Inside Docker |

---

## Production Checklist

Before deploying to production:

- [ ] Generate strong `JWT_SECRET` (≥32 chars, random)
- [ ] Generate strong `ENCRYPTION_KEY` (≥32 chars, random)
- [ ] Change `POSTGRES_PASSWORD` to a secure value
- [ ] Update `VITE_API_BASE_URL` to production domain (e.g., `https://api.padelpro.com`)
- [ ] Set `SPRING_PROFILES_ACTIVE=prod`
- [ ] Enable HTTPS (reverse proxy or load balancer)
- [ ] Set up backup strategy for PostgreSQL volume
- [ ] Configure resource limits in docker-compose.yml
- [ ] Review security groups / firewall rules

---

## Architecture

```
┌─────────────────────────────────────────────┐
│         Docker Compose (local dev)          │
├─────────────────────────────────────────────┤
│                                             │
│  ┌──────────────┐  ┌────────────────────┐  │
│  │   Frontend   │  │     Backend        │  │
│  │ React+Vite   │  │  Spring Boot 3.2   │  │
│  │  :5173       │  │     :8080          │  │
│  └──────┬───────┘  └────────┬───────────┘  │
│         │                   │              │
│         └───────────────────┘              │
│            HTTP/JSON via                   │
│         http://backend:8080                │
│                   │                        │
│         ┌─────────▼──────────┐             │
│         │   PostgreSQL 15    │             │
│         │   :5432            │             │
│         │  (padelpro_db)     │             │
│         └────────────────────┘             │
│                                             │
└─────────────────────────────────────────────┘
```

---

## Next Steps

1. ✅ Start the app: `docker-compose up -d`
2. 📖 Read [README.md](README.md) for architecture details
3. 🔐 Review [docs/security-design.md](docs/security-design.md) for JWT/auth flow
4. 📋 Check [backlog.md](backlog.md) for planned features
5. 🗂️ Explore [openspec/](openspec/) for capability specifications

---

## Support

- 🐛 **Issues**: [GitHub Issues](https://github.com/lcasadov/PadelPro/issues)
- 📧 **Email**: lcasadov@gmail.com
- 📚 **Docs**: See `docs/` folder for detailed specifications

Happy coding! 🎾
