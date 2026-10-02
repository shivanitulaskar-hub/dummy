# syntax=docker/dockerfile:1
# ---- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Tests need Docker (Testcontainers), so they run outside the image build: `mvn verify` locally / in CI.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/console-adda.jar /workspace/app.jar

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/logs && chown -R app:app /app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
ENV TZ=UTC \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    LOGGING_FILE_PATH=/app/logs
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
