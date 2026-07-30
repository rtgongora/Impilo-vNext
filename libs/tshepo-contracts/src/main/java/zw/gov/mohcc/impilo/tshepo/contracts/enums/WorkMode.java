package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * The type of work being performed within a resolved work context — the
 * dimension the Work Context Resolution programme adds alongside facility/
 * role: "Facility Mode is the environment. Role determines authority. Mode
 * determines the type of work."
 *
 * <p>Mode is a governed authority dimension, not a UI selector: it is minted
 * into the duty token's {@code context_claims} and read only from the token
 * at the PDP — never from a client-supplied header, for the same reason
 * {@code PolicyEngine.effectiveJurisdiction()} never trusts a header over
 * the token. Grants (which {@code role_template_id} may hold which mode) are
 * a governed table in {@code tshepo_authz.role_template_mode}, not hardcoded
 * here — this enum is the closed vocabulary, not the authorization.</p>
 *
 * <p>District/Provincial/National management deliberately collapse into the
 * single {@link #JURISDICTION_OPERATIONS} mode: the geographic breadth is
 * {@code wgv_jurisdiction.jurisdiction_type}, a dimension the duty token
 * already carries via {@code jurisdiction_code} — three modes differing only
 * in geographic level would duplicate that, not add information.</p>
 */
public enum WorkMode {

    /** Direct patient care at a facility, department, ward or service point. */
    CLINICAL_CARE(AnchorKind.FACILITY, ClinicalDataAccess.IDENTIFIED, PurposeOfUse.TREATMENT),

    /** Direct patient care performed remotely — telemedicine, a virtual specialist pool. */
    VIRTUAL_CARE(AnchorKind.FACILITY_OR_ORGANISATION, ClinicalDataAccess.IDENTIFIED, PurposeOfUse.TREATMENT),

    /** Managing a facility department or service line — staffing, queues, readiness for that department only. */
    DEPARTMENT_MANAGEMENT(AnchorKind.FACILITY, ClinicalDataAccess.AGGREGATE, PurposeOfUse.OPERATIONS),

    /** Managing a whole facility — staffing, resources, incidents, configuration. */
    FACILITY_MANAGEMENT(AnchorKind.FACILITY, ClinicalDataAccess.AGGREGATE, PurposeOfUse.OPERATIONS),

    /** Geographic oversight at district, provincial or national level (see class doc). */
    JURISDICTION_OPERATIONS(AnchorKind.JURISDICTION, ClinicalDataAccess.AGGREGATE, PurposeOfUse.PUBLIC_HEALTH),

    /** A national/provincial health-programme workspace spanning facilities and geography. */
    PROGRAMME_MANAGEMENT(AnchorKind.PROGRAMME, ClinicalDataAccess.AGGREGATE, PurposeOfUse.PUBLIC_HEALTH),

    /** Platform/infrastructure support at organisation scope — never clinical, never facility-operator. */
    TECHNICAL_SUPPORT(AnchorKind.ORGANISATION, ClinicalDataAccess.NONE, PurposeOfUse.SYSTEM),

    /** Platform/infrastructure support scoped to one facility's technology, not its clinical operations. */
    FACILITY_SUPPORT(AnchorKind.FACILITY, ClinicalDataAccess.NONE, PurposeOfUse.OPERATIONS),

    /** A regulator or council acting on its statutory mandate. Never a facility operator. */
    REGULATORY_OPERATIONS(AnchorKind.ORGANISATION, ClinicalDataAccess.NONE, PurposeOfUse.REGULATORY_DUTY),

    /** A regulator conducting an inspection or compliance review of a specific facility. */
    INSPECTION_COMPLIANCE(AnchorKind.ORGANISATION, ClinicalDataAccess.NONE, PurposeOfUse.REGULATORY_DUTY),

    /**
     * Care delivered away from the facility — household screening, community
     * immunization, defaulter follow-up. Identified, because the work is recording
     * screenings and immunizations against a named person; a mode that could not
     * would describe none of it. Anchored to the dispatching facility or the
     * programme running the campaign, which are the two ways outreach is organised.
     */
    COMMUNITY_OUTREACH(AnchorKind.FACILITY_OR_PROGRAMME, ClinicalDataAccess.IDENTIFIED, PurposeOfUse.TREATMENT),

    /**
     * Specimen and consignment custody in transit — seals, cold-chain readings,
     * proof of delivery. {@code NONE} is the point of the mode: a courier must be
     * able to prove what they carried and under what conditions, and must never be
     * able to read what it said. Logistics authority, not clinical authority.
     */
    SPECIMEN_TRANSPORT(AnchorKind.FACILITY_OR_ORGANISATION, ClinicalDataAccess.NONE, PurposeOfUse.OPERATIONS);

    /** What kind of anchor(s) a token minted in this mode must carry — validated at mint time. */
    public enum AnchorKind {
        FACILITY, ORGANISATION, JURISDICTION, PROGRAMME, FACILITY_OR_ORGANISATION, FACILITY_OR_PROGRAMME
    }

    /**
     * The clinical-data envelope this mode carries, independent of role.
     * {@code IDENTIFIED} is the only level at which a clinical canonical
     * role (CLINICIAN, DOCTOR, NURSE, ...) is folded into the effective
     * role set at the PDP — see {@code PolicyEngine.bindWorkContext}.
     */
    public enum ClinicalDataAccess {
        /** Full identified clinical record access, subject to the usual scope/consent checks. */
        IDENTIFIED,
        /** Aggregate/de-identified operational signals only — no patient-identified data. */
        AGGREGATE,
        /** No clinical data of any kind. */
        NONE
    }

    private final AnchorKind anchorKind;
    private final ClinicalDataAccess clinicalDataAccess;
    private final PurposeOfUse defaultPurposeOfUse;

    WorkMode(AnchorKind anchorKind, ClinicalDataAccess clinicalDataAccess, PurposeOfUse defaultPurposeOfUse) {
        this.anchorKind = anchorKind;
        this.clinicalDataAccess = clinicalDataAccess;
        this.defaultPurposeOfUse = defaultPurposeOfUse;
    }

    public AnchorKind anchorKind() {
        return anchorKind;
    }

    public ClinicalDataAccess clinicalDataAccess() {
        return clinicalDataAccess;
    }

    public PurposeOfUse defaultPurposeOfUse() {
        return defaultPurposeOfUse;
    }

    /** True only for {@link ClinicalDataAccess#IDENTIFIED} — the sole level that folds a clinical canonical role. */
    public boolean grantsIdentifiedClinicalRead() {
        return clinicalDataAccess == ClinicalDataAccess.IDENTIFIED;
    }

    /**
     * Parse a mode name defensively — an unparseable mode must never default
     * to the most permissive mode, so callers that need a fallback should
     * check {@link java.util.Optional#isEmpty()} and fail closed, not
     * substitute a value here.
     */
    public static java.util.Optional<WorkMode> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(WorkMode.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
