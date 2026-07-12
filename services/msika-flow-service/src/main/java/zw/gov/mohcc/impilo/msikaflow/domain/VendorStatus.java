package zw.gov.mohcc.impilo.msikaflow.domain;

public enum VendorStatus {
    APPLIED,
    PENDING_VERIFICATION,
    APPROVED,
    ACTIVE,
    /** Application rejected by ops review (OpsService.rejectReview). */
    REJECTED,
    SUSPENDED,
    DEACTIVATED
}
