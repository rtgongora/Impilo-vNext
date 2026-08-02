package zw.gov.mohcc.impilo.tshepo.contracts.v1;

/**
 * Lawful bases are related to consent but not interchangeable with it.
 *
 * <p>Ownership of grant/revocation SoR (Mvumo vs tshepo-consent-service) is unresolved —
 * see Checkpoint 1 {@code CONSENT_CONTRACT_INCOMPATIBILITY.md}. Adapters must not resolve it.</p>
 */
public enum LawfulBasisType {
    EXPLICIT_CONSENT,
    DIRECT_CARE_RELATIONSHIP,
    STATUTORY_AUTHORITY,
    PUBLIC_HEALTH_AUTHORITY,
    EMERGENCY_BREAK_GLASS,
    OTHER_GOVERNED
}
