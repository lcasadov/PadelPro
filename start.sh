#!/bin/bash
set -e

echo "🚀 PadelPro — Starting Application"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker from https://www.docker.com/get-docker/"
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install from https://docs.docker.com/compose/install/"
    exit 1
fi

# Setup .env if it doesn't exist
if [ ! -f ".env" ]; then
    echo "📝 Creating .env from .env.example..."
    cp .env.example .env
    echo "✅ .env created. Please review and customize if needed."
    echo ""
fi

# Build images
echo "🔨 Building Docker images (this may take 2-3 minutes on first run)..."
docker-compose build

echo ""
echo "🚀 Starting services..."
docker-compose up -d

echo ""
echo "⏳ Waiting for services to be ready..."
sleep 5

# Check if services are healthy
echo "🏥 Checking service health..."
docker-compose ps

echo ""
echo "✅ PadelPro is starting!"
echo ""
echo "📍 Access the application at:"
echo "   Frontend:  http://localhost:5173"
echo "   Backend:   http://localhost:8080"
echo "   API Docs:  http://localhost:8080/swagger-ui.html"
echo ""
echo "📋 View logs:"
echo "   docker-compose logs -f backend   # Backend logs"
echo "   docker-compose logs -f frontend  # Frontend logs"
echo "   docker-compose logs -f db        # Database logs"
echo ""
echo "🛑 Stop services:"
echo "   docker-compose down"
echo ""
echo "📖 For more help, see QUICKSTART.md"
