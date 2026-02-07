package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * Dual-mode access control.
 *
 * <p>INTERNAL = platform service-to-service (mTLS verified by Envoy).
 * EXTERNAL = public-facing (OIDC session via Keycloak/eSignet).</p>
 */
public enum AccessMode {
    INTERNAL,
    EXTERNAL
}
