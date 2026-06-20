# syntax=docker/dockerfile:1

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copia o wrapper e os arquivos de build primeiro (melhor cache de camadas)
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY src ./src

RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar -x test

# Extrai as camadas do jar do Spring Boot (layertools) para cache eficiente no runtime
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination build/extracted

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# curl para o healthcheck do container
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Usuário não-root (princípio do menor privilégio)
RUN groupadd --system app && useradd --system --gid app --no-create-home app

# Camadas em ordem de menor → maior volatilidade (melhor reaproveitamento de cache)
COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./

USER app
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -fs http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
