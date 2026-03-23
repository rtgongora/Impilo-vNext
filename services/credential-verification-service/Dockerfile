FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgrp && adduser -S appusr -G appgrp

WORKDIR /app
COPY target/credential-verification-service-*.jar app.jar

RUN chown -R appusr:appgrp /app
USER appusr

EXPOSE 8094
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8094/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
