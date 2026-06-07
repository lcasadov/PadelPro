# Multi-stage build: compile + runtime
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
COPY src src

RUN mvn clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the JAR from builder
COPY --from=builder /build/target/padelpro-backend-*.jar app.jar
RUN chown appuser:appgroup app.jar

# Health check endpoint
RUN apk add --no-cache curl

EXPOSE 8080

# Run as non-root user
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
