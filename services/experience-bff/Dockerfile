FROM eclipse-temurin:21-jre

# Runtime image truth: bust cache before packaging the JAR artifact.
ARG SOURCE_COMMIT=unknown
ARG CACHE_BUST=unknown
RUN echo "cachebust=${CACHE_BUST} commit=${SOURCE_COMMIT}" > /dev/null

RUN groupadd -r impilo && useradd -r -g impilo impilo

WORKDIR /app
COPY target/experience-bff-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8160
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
