FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY services/pom.xml services/pom.xml
COPY libs/security-baseline/pom.xml libs/security-baseline/pom.xml
COPY libs/security-baseline/src libs/security-baseline/src
COPY services/shared-core/pom.xml services/shared-core/pom.xml
COPY services/shared-core/src services/shared-core/src
COPY services/guidance-service/pom.xml services/guidance-service/pom.xml
COPY services/guidance-service/src services/guidance-service/src
WORKDIR /build/services
RUN mvn -q -pl guidance-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/services/guidance-service/target/guidance-service-*.jar app.jar
EXPOSE 8260
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
