# ---- Build ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY ../pom.xml ../pom.xml
COPY ../shared-core ../shared-core
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    cd .. && mvn -pl shared-core,varapi-service -am package -DskipTests -q

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgrp && adduser -S appusr -G appgrp
WORKDIR /app
COPY --from=builder /build/target/varapi-service-*.jar app.jar
RUN chown -R appusr:appgrp /app
USER appusr
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
