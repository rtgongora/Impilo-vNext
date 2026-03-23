FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S impilo && adduser -S impilo -G impilo

WORKDIR /app
COPY target/tshepo-authz-service-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8081 9090

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
