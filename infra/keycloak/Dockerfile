ARG KEYCLOAK_VERSION=26.7.0
FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION} AS builder

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}
ARG SOURCE_COMMIT=unknown
ARG SOURCE_BRANCH=unknown
ARG SOURCE_TREE=unknown
ARG SOURCE_DIRTY=unknown
ARG BUILD_DATE=unknown
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --chown=1000:1000 themes/impilo/ /opt/keycloak/themes/impilo/

LABEL org.opencontainers.image.revision=${SOURCE_COMMIT} \
      org.opencontainers.image.created=${BUILD_DATE} \
      org.opencontainers.image.source="https://github.com/rtgongora/Impilo-vNext" \
      zw.gov.mohcc.impilo.source.branch=${SOURCE_BRANCH} \
      zw.gov.mohcc.impilo.source.tree=${SOURCE_TREE} \
      zw.gov.mohcc.impilo.source.dirty=${SOURCE_DIRTY}

USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
