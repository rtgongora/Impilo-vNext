FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S impilo && adduser -S impilo -G impilo

WORKDIR /app
COPY target/card-print-agent-*.jar app.jar

RUN mkdir -p /tmp/card-print-output && \
    chown -R impilo:impilo /app /tmp/card-print-output

USER impilo

EXPOSE 8091

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
