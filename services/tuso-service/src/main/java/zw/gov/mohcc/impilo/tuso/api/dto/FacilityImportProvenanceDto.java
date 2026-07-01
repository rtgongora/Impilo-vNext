package zw.gov.mohcc.impilo.tuso.api.dto;

import java.util.List;

/**
 * Import provenance + configuration-completeness checklist for a facility. Surfaces the product truth
 * that an imported facility is NOT operational merely by appearing in a master list: the internal
 * identity, the (external, public) administrative code, the missing acceptable fields, and the
 * downstream configuration items that are still outstanding — none faked, none defaulted.
 */
public record FacilityImportProvenanceDto(
        Identity identity,
        AcceptableMissing acceptableMissing,
        List<ChecklistItem> checklist,
        String downstreamMaterialisationStatus) {

    /**
     * Distinguishes the internal facility identity from the public administrative code and other external
     * identifiers, so the UI never conflates them.
     */
    public record Identity(
            Long internalFacilityId,
            String facilityUuid,
            String facilityCode,
            /** Label to use in UI: never "Facility ID". */
            String facilityCodeLabel,
            List<ExternalIdentifier> externalIdentifiers,
            String sourceLabel,
            String sourceRow,
            boolean importedFromMaster,
            String lifecycleState,
            String operationalStatus) {}

    public record ExternalIdentifier(String system, String value, String issuingAuthority) {}

    /** Acceptable-missing fields kept visible as missing (no fake defaults). */
    public record AcceptableMissing(
            boolean missingLatitude,
            boolean missingLongitude,
            boolean missingFacilityType,
            boolean missingOwnership,
            boolean missingOperatingStatus) {}

    /**
     * One configuration item. {@code status} ∈ PRESENT | MISSING | PENDING_DOWNSTREAM.
     * {@code owner} names the system of record responsible (TUSO for facility-owned config; PCT/VASHANDI/
     * DURA for downstream-materialised config).
     */
    public record ChecklistItem(String key, String label, String status, String owner) {}
}
