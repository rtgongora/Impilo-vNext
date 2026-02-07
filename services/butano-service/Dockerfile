# ═══════════════════════════════════════════════════════════
# BUTANO — Shared Health Record Service (HAPI FHIR R4 JPA)
# Multi-stage build, non-root runtime
# ═══════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY ../pom.xml ../pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    cd .. && mvn -pl butano-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S impilo && adduser -S impilo -G impilo
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN mkdir -p /tmp/butano-lucene && chown impilo:impilo /tmp/butano-lucene
USER impilo
EXPOSE 8090
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
