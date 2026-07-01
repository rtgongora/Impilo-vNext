package zw.gov.mohcc.impilo.tuso.persistence.entity;

import java.util.Map;

/**
 * Canonical taxonomy of <b>external</b> facility identifier systems stored in
 * {@link FacilityIdentifierEntity} (the {@code system} column).
 *
 * <p><b>Security &amp; identity doctrine (binding).</b> None of these values are secrets, credentials,
 * or proof of facility membership. They are <em>public administrative / interoperability identifiers</em>
 * used for matching, reporting, DHIS2 alignment, and cross-registry reconciliation. The <b>internal</b>
 * facility identity is NOT in this taxonomy — it is {@link FacilityEntity#getFacilityUuid()} (opaque,
 * immutable, non-reused canonical UUID) and the numeric surrogate {@link FacilityEntity#getId()}. Only
 * the internal identity, combined with verified provider/staff assignment and Tshepo/OPA policy, may
 * authorise access or Facility Mode. A facility code (or any value below) must <b>never</b>:</p>
 * <ul>
 *   <li>be treated as authentication or a password;</li>
 *   <li>by itself grant facility access or switch a user into Facility Mode;</li>
 *   <li>by itself prove that a provider, administrator, data clerk, or operator belongs to a facility;</li>
 *   <li>be used as a database primary key or as the route identity for protected operations.</li>
 * </ul>
 * These values are for matching and interoperability only.
 */
public final class FacilityIdentifierSystem {

    private FacilityIdentifierSystem() {}

    /**
     * Import/reconciliation match key for a national master-list row. This is TUSO's internal, stable
     * correlation handle for a source row (so re-imports match the same facility and never regenerate a
     * new internal id). It is the {@code facility_uid} carried by the master pack — not the public code.
     */
    public static final String MASTER_FACILITY_UID = "MASTER_FACILITY_UID";

    /** Zimbabwe national administrative facility code (public). Used for reporting / lookup, never auth. */
    public static final String NATIONAL_FACILITY_CODE = "NATIONAL_FACILITY_CODE";

    /** DHIS2 organisation-unit identifier for aggregate reporting alignment. */
    public static final String DHIS2_ORG_UNIT_ID = "DHIS2_ORG_UNIT_ID";

    /** Identifier the facility carried in a legacy EHR, retained for reconciliation during migration. */
    public static final String LEGACY_EHR_FACILITY_ID = "LEGACY_EHR_FACILITY_ID";

    /** Health Professions Authority registration number. */
    public static final String HPA_REGISTRATION_NUMBER = "HPA_REGISTRATION_NUMBER";

    /** MoHCC facility registry code. */
    public static final String MOHCC_FACILITY_REGISTRY_CODE = "MOHCC_FACILITY_REGISTRY_CODE";

    /** Local-authority (council) facility code. */
    public static final String LOCAL_AUTHORITY_CODE = "LOCAL_AUTHORITY_CODE";

    /** Provenance handle for the specific source row a facility was absorbed from (audit/traceability). */
    public static final String IMPORT_SOURCE_ROW_ID = "IMPORT_SOURCE_ROW_ID";

    /**
     * Human-readable issuing authority per system, surfaced so the frontend can label an identifier by
     * its source instead of implying it is an internal Impilo identity.
     */
    private static final Map<String, String> ISSUING_AUTHORITY = Map.of(
            MASTER_FACILITY_UID, "Impilo TUSO (import correlation key)",
            NATIONAL_FACILITY_CODE, "Ministry of Health and Child Care (national facility register)",
            DHIS2_ORG_UNIT_ID, "DHIS2 national HMIS",
            LEGACY_EHR_FACILITY_ID, "Legacy EHR system",
            HPA_REGISTRATION_NUMBER, "Health Professions Authority",
            MOHCC_FACILITY_REGISTRY_CODE, "Ministry of Health and Child Care facility registry",
            LOCAL_AUTHORITY_CODE, "Local authority / council",
            IMPORT_SOURCE_ROW_ID, "Impilo TUSO import pipeline");

    /** @return issuing authority label for a known system, or {@code "External registry"} otherwise. */
    public static String issuingAuthority(String system) {
        return ISSUING_AUTHORITY.getOrDefault(system, "External registry");
    }

    /**
     * A facility code / administrative code is never an internal identity or credential. This holds for
     * every system in this taxonomy — they are all external, public matching identifiers.
     *
     * @return {@code false} always; retained as an explicit, self-documenting guard for callers that
     *         must assert "this identifier is not an authority" at the point of use.
     */
    public static boolean isInternalIdentity(String system) {
        return false;
    }
}
