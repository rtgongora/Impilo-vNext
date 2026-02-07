FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY ../pom.xml ../pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn -f pom.xml package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S impilo && adduser -S impilo -G impilo

WORKDIR /app
COPY --from=build /build/target/card-print-agent-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8091

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
