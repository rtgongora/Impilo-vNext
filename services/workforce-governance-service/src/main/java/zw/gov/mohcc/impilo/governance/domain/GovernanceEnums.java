package zw.gov.mohcc.impilo.governance.domain;

/**
 * Lifecycle and classification enums for workforce governance.
 */
public final class GovernanceEnums {

    private GovernanceEnums() {}

    public enum OrganisationStatus {
        DRAFT, ACTIVE, INACTIVE, SUSPENDED, CLOSED
    }

    public enum OrganisationUnitStatus {
        ACTIVE, INACTIVE, CLOSED
    }

    public enum JurisdictionStatus {
        ACTIVE, INACTIVE, RETIRED
    }

    public enum LinkStatus {
        PROPOSED, ACTIVE, INACTIVE, EXPIRED, TERMINATED
    }

    public enum AssignmentStatus {
        PROPOSED, PENDING_APPROVAL, ACTIVE, INACTIVE, EXPIRED, SUSPENDED, TERMINATED
    }

    public enum AssignmentSubjectType {
        USER, PROVIDER, SYSTEM_ACTOR
    }

    public enum AssignmentTargetType {
        ORGANISATION,
        ORGANISATION_UNIT,
        FACILITY,
        SITE,
        JURISDICTION,
        PROGRAM,
        VIRTUAL_HUB,
        MULTI_SITE_GROUP,
        SERVICE_NETWORK
    }

    public enum JurisdictionType {
        WARD,
        DISTRICT,
        PROVINCE,
        NATIONAL,
        MUNICIPAL,
        CATCHMENT,
        REGULATORY_ZONE,
        PROGRAM_REGION,
        SERVICE_TERRITORY,
        OTHER
    }

    public enum RoleCategory {
        CLINICAL,
        ADMINISTRATIVE,
        MANAGERIAL,
        SUPERVISORY,
        SUPPORT,
        REGULATORY,
        LOGISTICS,
        TELEHEALTH,
        QUALITY,
        PROGRAMMATIC
    }

    public enum RoleLevel {
        FACILITY_LEVEL,
        MULTI_SITE,
        JURISDICTIONAL,
        ORGANISATIONAL,
        NATIONAL
    }
}
