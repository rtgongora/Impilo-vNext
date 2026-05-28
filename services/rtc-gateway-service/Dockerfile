FROM eclipse-temurin:21-jre-jammy

RUN groupadd -r impilo && useradd -r -g impilo impilo

WORKDIR /app
COPY target/rtc-gateway-service-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8195

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
