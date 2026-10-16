# Multi-stage Dockerfile for Gatehouse Gateway
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy maven wrapper and pom.xml
COPY mvnw* ./
COPY .mvn/ .mvn/
COPY pom.xml ./

# Pre-fetch dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests

# Production Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S gatehouse && adduser -S gatehouse -G gatehouse
USER gatehouse:gatehouse

COPY --from=builder /workspace/target/gatehouse-gateway-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
