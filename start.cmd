@echo off
setlocal enabledelayedexpansion

echo 🚀 PadelPro — Starting Application
echo.

REM Check if Docker is installed
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker is not installed. Please install from https://www.docker.com/get-docker/
    pause
    exit /b 1
)

REM Check if Docker Compose is installed
docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker Compose is not installed. Please install from https://docs.docker.com/compose/install/
    pause
    exit /b 1
)

REM Setup .env if it doesn't exist
if not exist ".env" (
    echo 📝 Creating .env from .env.example...
    copy .env.example .env
    echo ✅ .env created. Please review and customize if needed.
    echo.
)

REM Build images
echo 🔨 Building Docker images (this may take 2-3 minutes on first run^)...
docker-compose build

echo.
echo 🚀 Starting services...
docker-compose up -d

echo.
echo ⏳ Waiting for services to be ready...
timeout /t 5

REM Check if services are healthy
echo 🏥 Checking service health...
docker-compose ps

echo.
echo ✅ PadelPro is starting!
echo.
echo 📍 Access the application at:
echo    Frontend:  http://localhost:5173
echo    Backend:   http://localhost:8080
echo    API Docs:  http://localhost:8080/swagger-ui.html
echo.
echo 📋 View logs:
echo    docker-compose logs -f backend   ^# Backend logs
echo    docker-compose logs -f frontend  ^# Frontend logs
echo    docker-compose logs -f db        ^# Database logs
echo.
echo 🛑 Stop services:
echo    docker-compose down
echo.
echo 📖 For more help, see QUICKSTART.md
echo.
pause
