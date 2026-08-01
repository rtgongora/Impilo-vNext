ARG KEYCLOAK_VERSION=26.7.0
FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION} AS builder

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --chown=1000:1000 themes/impilo/ /opt/keycloak/themes/impilo/

USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
