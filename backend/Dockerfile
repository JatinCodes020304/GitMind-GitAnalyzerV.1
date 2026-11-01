# Multi-stage build for production efficiency
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build the application
RUN apk add --no-cache maven
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S gitmind && adduser -S gitmind -G gitmind

# Copy built artifact from builder
COPY --from=builder /app/target/*.jar app.jar

# Set ownership
RUN chown -R gitmind:gitmind /app

USER gitmind

EXPOSE 8080

# JVM optimizations for containers
# Tuned for small instances (e.g. 512 MB free tier): faster startup, lower memory use.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss512k -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
