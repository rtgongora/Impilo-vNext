FROM eclipse-temurin:21-jre

# Runtime image truth: bust cache before packaging the JAR artifact, and bake the
# build provenance into the image so /health/version reports the real commit/branch/date
# regardless of how the deployment is wired.
ARG SOURCE_COMMIT=unknown
ARG SOURCE_BRANCH=unknown
ARG BUILD_DATE=unknown
ARG CACHE_BUST=unknown
RUN echo "cachebust=${CACHE_BUST} commit=${SOURCE_COMMIT}" > /dev/null
ENV IMPILO_GIT_COMMIT=${SOURCE_COMMIT} \
    IMPILO_GIT_BRANCH=${SOURCE_BRANCH} \
    IMPILO_BUILD_DATE=${BUILD_DATE}

RUN groupadd -r impilo && useradd -r -g impilo impilo

WORKDIR /app
COPY target/experience-bff-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8160
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
