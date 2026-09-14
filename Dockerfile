# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

# Copy source code
COPY src src

# Build the application
RUN chmod +x mvnw && \
    ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Install xmllint for XML validation (optional, for debugging)
RUN apk add --no-cache libxml2-utils

# Copy the built JAR from builder stage
COPY --from=builder /app/target/assistance-0.0.1-SNAPSHOT.jar app.jar

# Create a non-root user for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

USER appuser

# Expose port for Spring Boot Actuator (if needed)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD java -jar app.jar --spring.profiles.active=health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
