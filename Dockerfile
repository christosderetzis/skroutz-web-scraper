# Multi-stage build for optimal image size

# Stage 1: Build the application
FROM gradle:9.5.1-jdk25 AS builder

WORKDIR /app

# Copy gradle files first for better layer caching
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Create a minimal gradle.properties for Docker build
RUN echo "org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m" > gradle.properties && \
    echo "org.gradle.parallel=true" >> gradle.properties && \
    echo "org.gradle.caching=true" >> gradle.properties

# Download dependencies (this layer will be cached if dependencies don't change)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds, tests should run in CI)
RUN gradle bootJar --no-daemon -x test

# Stage 2: Create the runtime image
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown spring:spring app.jar

USER spring:spring

# Expose the application port
EXPOSE 8082

# Health check (using Swagger API docs endpoint)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api-docs || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]