package zw.gov.mohcc.impilo.vito.events;

/**
 * Dual emit policy for v1.1 event envelope support.
 *
 * LEGACY_ONLY  — keep current behavior only (topics vito.* raw payload)
 * V1_1_ONLY    — emit only v1.1 envelope to impilo.vito.* topics
 * DUAL         — emit both legacy and v1.1
 */
public enum EmitMode {
    LEGACY_ONLY,
    V1_1_ONLY,
    DUAL
}
