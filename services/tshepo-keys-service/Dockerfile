FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build
COPY pom.xml .
COPY src ./src

# If using a multi-module build, the parent and sibling modules should be
# pre-installed in the local Maven repository (CI pipeline handles this).
# For standalone builds, use: mvn -pl tshepo-keys-service -am package -DskipTests
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S impilo && adduser -S impilo -G impilo

WORKDIR /app
COPY --from=build /build/target/tshepo-keys-service-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8184

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
