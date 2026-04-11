FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY services/pom.xml services/pom.xml
COPY libs/security-baseline/pom.xml libs/security-baseline/pom.xml
COPY libs/security-baseline/src libs/security-baseline/src
COPY services/shared-core/pom.xml services/shared-core/pom.xml
COPY services/shared-core/src services/shared-core/src
COPY services/clinical-knowledge-platform-service/pom.xml services/clinical-knowledge-platform-service/pom.xml
COPY services/clinical-knowledge-platform-service/src services/clinical-knowledge-platform-service/src
WORKDIR /build/services
RUN mvn -q -pl clinical-knowledge-platform-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/services/clinical-knowledge-platform-service/target/clinical-knowledge-platform-service-*.jar app.jar
# National reference PDF for optional in-container ingestion (path aligned with application.yml defaults)
COPY docs/reference/edliz-2025/EDLIZ-2025-final-for-circulation.pdf edliz/EDLIZ-2025-final-for-circulation.pdf
ENV CLINICAL_INGESTION_WORKING_DIR=/app
ENV EDLIZ_REFERENCE_PDF_PATH=edliz/EDLIZ-2025-final-for-circulation.pdf
EXPOSE 8270
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
