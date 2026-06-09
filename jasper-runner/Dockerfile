# Estágio 1: compila o JAR (Maven + JDK 17)
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -q clean package -DskipTests

# Estágio 2: runtime com Java 17 (JRE) — não é necessário instalar Java no host
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system appgroup \
    && adduser --system --ingroup appgroup appuser

COPY --from=build /workspace/target/jasper-runner-1.0.0.jar /app/jasper-runner.jar
COPY src/main/resources/application.yml /app/application.yml
COPY src/main/resources/jasperreports.properties /app/jasperreports.properties

RUN mkdir -p /app/reports /app/logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8090/login >/dev/null || exit 1

ENTRYPOINT ["java","-jar","/app/jasper-runner.jar","--spring.config.location=file:/app/application.yml"]
