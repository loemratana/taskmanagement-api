# ========= Build =========
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Build scripts first: this layer (and the Gradle cache below) is reused until they change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# A Windows checkout can give gradlew CRLF line endings, which break it on Linux.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src src

# The cache mount keeps downloaded dependencies between builds without putting them in the image.
# Tests and Checkstyle run in CI (./gradlew build), not here.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -x test \
    && cp build/libs/*.jar app.jar \
    # Split the fat jar into layers: dependencies rarely change, so a code-only change ships a small layer.
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ========= Runtime =========
FROM eclipse-temurin:21-jre

# curl: used by the health checks below and in docker-compose.yml. Then an unprivileged user to run as.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --no-create-home app

WORKDIR /app

# Least- to most-often changing, so rebuilds reuse the big layers.
COPY --from=builder --chown=app:app /app/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /app/extracted/application/ ./

USER app

# prod: every secret must be set (fails fast otherwise), Swagger off, Flyway validates the schema.
# docker-compose.yml overrides this with its own profile. Override any of these with -e.
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Phnom_Penh \
    # Size the heap from the container's memory limit, and restart cleanly instead of limping on after an OOM.
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Ready = started and the database answers (same probe as docker-compose.yml).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT:-8080}/management/health/readiness" || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
