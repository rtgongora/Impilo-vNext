FROM eclipse-temurin:21-jre-alpine

ARG SOURCE_COMMIT=unknown
ARG SOURCE_BRANCH=unknown
ARG SOURCE_TREE=unknown
ARG SOURCE_DIRTY=unknown
ARG BUILD_DATE=unknown
ENV IMPILO_GIT_COMMIT=${SOURCE_COMMIT} \
    IMPILO_GIT_BRANCH=${SOURCE_BRANCH} \
    IMPILO_BUILD_DATE=${BUILD_DATE}
LABEL org.opencontainers.image.revision=${SOURCE_COMMIT} \
      org.opencontainers.image.created=${BUILD_DATE} \
      org.opencontainers.image.source="https://github.com/rtgongora/Impilo-vNext" \
      zw.gov.mohcc.impilo.source.branch=${SOURCE_BRANCH} \
      zw.gov.mohcc.impilo.source.tree=${SOURCE_TREE} \
      zw.gov.mohcc.impilo.source.dirty=${SOURCE_DIRTY}

RUN addgroup -S impilo && adduser -S impilo -G impilo
WORKDIR /app
COPY target/tshepo-authz-service-*.jar app.jar
RUN chown -R impilo:impilo /app
USER impilo
EXPOSE 8081 9090
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
