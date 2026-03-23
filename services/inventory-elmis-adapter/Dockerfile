FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S impilo && adduser -S impilo -G impilo

WORKDIR /app
COPY target/inventory-elmis-adapter-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8099

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
