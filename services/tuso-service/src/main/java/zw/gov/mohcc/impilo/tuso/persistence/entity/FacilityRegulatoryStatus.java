package zw.gov.mohcc.impilo.tuso.persistence.entity;

public enum FacilityRegulatoryStatus {
    DRAFT,
    /**
     * Absorbed from a national facility master list (e.g. MASTER_HEALTH_FACILITY_2024_07_23): the
     * facility identity/classification exists, but practitioner-in-charge, regulatory compliance,
     * contacts, service points, workspaces, queues, staffing, stores and digital readiness are NOT
     * yet configured. Imported facilities are NOT operational merely by appearing in the master list.
     */
    IMPORTED_PENDING_CONFIGURATION,
    APPLICATION_IN_PROGRESS,
    UNDER_INITIAL_REVIEW,
    PENDING_INSPECTION,
    INSPECTION_IN_PROGRESS,
    PENDING_RECTIFICATION,
    PENDING_COMMITTEE_REVIEW,
    APPROVED_FOR_REGISTRATION,
    REGISTERED_ACTIVE,
    RENEWAL_DUE,
    RENEWAL_IN_PROGRESS,
    RESTRICTED,
    SUSPENDED,
    PENDING_CLOSURE,
    CLOSED,
    VOLUNTARILY_CLOSED
}
