FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY pom.xml .
COPY ../pom.xml ../pom.xml
COPY src src

RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S impilo && adduser -S impilo -G impilo

WORKDIR /app
COPY --from=build /workspace/target/inventory-service-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8098

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
