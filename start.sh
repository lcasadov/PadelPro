#!/bin/bash
set -e

echo "🚀 PadelPro — Starting Application"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker from https://www.docker.com/get-docker/"
    exit 1
fi

# Check if Docker Compose is installed (v1 or v2 plugin)
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
elif docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    echo "❌ Docker Compose is not installed. Please install from https://docs.docker.com/compose/install/"
    exit 1
fi

echo "✅ Using $DOCKER_COMPOSE"
echo ""

# Setup .env if it doesn't exist
if [ ! -f ".env" ]; then
    echo "📝 Creating .env from .env.example..."
    cp .env.example .env
    echo "✅ .env created. Please review and customize if needed."
    echo ""
fi

# Build images
echo "🔨 Building Docker images (this may take 2-3 minutes on first run)..."
$DOCKER_COMPOSE build

echo ""
echo "🚀 Starting services..."
$DOCKER_COMPOSE up -d

echo ""
echo "⏳ Waiting for services to be ready..."
sleep 5

# Check if services are healthy
echo "🏥 Checking service health..."
$DOCKER_COMPOSE ps

echo ""
echo "✅ PadelPro is starting!"
echo ""
echo "📍 Access the application at:"
echo "   Frontend:  http://localhost:5173"
echo "   Backend:   http://localhost:8080"
echo "   API Docs:  http://localhost:8080/swagger-ui.html"
echo ""
echo "📋 View logs:"
echo "   $DOCKER_COMPOSE logs -f backend   # Backend logs"
echo "   $DOCKER_COMPOSE logs -f frontend  # Frontend logs"
echo "   $DOCKER_COMPOSE logs -f db        # Database logs"
echo ""
echo "🛑 Stop services:"
echo "   $DOCKER_COMPOSE down"
echo ""
echo "📖 For more help, see QUICKSTART.md"
