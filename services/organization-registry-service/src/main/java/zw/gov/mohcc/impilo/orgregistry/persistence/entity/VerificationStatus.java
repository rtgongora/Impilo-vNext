package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

/**
 * Verification lifecycle shared by organizations and authorized representatives.
 */
public enum VerificationStatus {
    UNVERIFIED,
    PENDING_VERIFICATION,
    VERIFIED,
    REJECTED,
    EXPIRED,
    REVOKED
}
