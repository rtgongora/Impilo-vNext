FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# In real build, maven would be used; for container, copy pre-built jar
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S impilo && adduser -S impilo -G impilo
WORKDIR /app
COPY --from=build /build/target/mushex-service-*.jar app.jar
RUN chown -R impilo:impilo /app
USER impilo
EXPOSE 8087
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
