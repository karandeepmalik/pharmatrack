# ── Stage 1: Build ───────────────────────────────────────────────────
FROM --platform=linux/amd64 maven:3.9.5-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Cache dependencies separately from source
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────
FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine AS runtime

# Security: run as non-root
RUN addgroup -S pharma && adduser -S pharma -G pharma
USER pharma

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Healthcheck so orchestrators know when the app is ready
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
