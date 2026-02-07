# ============================================================================
# MSIKA Core — Products & Services Registry
# Multi-stage build: Maven build -> minimal JRE runtime
# ============================================================================

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S impilo && adduser -S impilo -G impilo

WORKDIR /app

COPY target/msika-service-*.jar app.jar

RUN chown -R impilo:impilo /app

USER impilo

EXPOSE 8086

ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
