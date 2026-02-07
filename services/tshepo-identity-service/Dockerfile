FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build
COPY pom.xml .
COPY src src

# If using a multi-module build, the parent POM and sibling libs must be available.
# In CI, rely on pre-built .jar via --build-arg or a mounted Maven cache.
COPY target/tshepo-identity-service-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S impilo && adduser -S impilo -G impilo

WORKDIR /app
COPY --from=build /build/app.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8181

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
