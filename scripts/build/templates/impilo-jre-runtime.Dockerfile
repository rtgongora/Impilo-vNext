# Shared Impilo Java runtime image — build context must include app.jar
FROM eclipse-temurin:21-jre
RUN groupadd -r impilo && useradd -r -g impilo impilo
WORKDIR /app
COPY app.jar /app/app.jar
RUN chown -R impilo:impilo /app
USER impilo
ARG APP_PORT=8080
EXPOSE ${APP_PORT}
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
