package zw.gov.mohcc.impilo.tshepo.authz.core;

import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataSensitivityClass;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataVisibilityTier;

/**
 * Maps resource types (from URL segments / resource registry) to a coarse sensitivity class
 * and the maximum visibility tier this resource type may ever receive.
 *
 * <h2>The confidential lane</h2>
 * <p>This classifier sees a URL, not a record, so it can only recognise a <em>lane</em>. The
 * convention it enforces: any route serving content classified {@code SPECIALLY_PROTECTED}
 * (sexual and reproductive health, HIV, mental health, safeguarding) MUST carry one of
 * {@link #PROTECTED_LANE_MARKERS} in its path. Route it under {@code /confidential/…} or
 * {@code /safeguarding/…} and the PDP treats the whole request as protected.</p>
 *
 * <p>Which <em>clinical codes</em> are confidential by nature is deliberately NOT decided here —
 * that is a governed, reviewable value set
 * ({@code https://impilo.gov.zw/fhir/ValueSet/confidential-clinical-category}, seeded in
 * zibo-service) which the clinical system of record consults when it stamps a record's sensitivity
 * class. This class is the coarse route gate; that value set is the content truth. Neither alone is
 * sufficient, and each assuming the other did the work is exactly why the class was decorative.</p>
 */
public final class ResourceSensitivityClassifier {

    /**
     * Path/resource-type segments that mark the confidential clinical lane. Kept deliberately
     * unambiguous — a short or common substring here would silently over-classify ordinary routes
     * and a control that fires everywhere teaches people to route around it.
     */
    private static final String[] PROTECTED_LANE_MARKERS = {
            "confidential", "safeguarding", "protected-disclosure"
    };

    private ResourceSensitivityClassifier() {
    }

    /** Whether this resource type sits in the confidential lane (see class docs). */
    public static boolean isSpeciallyProtected(String resourceType) {
        return classifyResource(resourceType) == DataSensitivityClass.SPECIALLY_PROTECTED;
    }

    public static DataSensitivityClass classifyResource(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return DataSensitivityClass.NON_SENSITIVE_OPERATIONAL;
        }
        String r = resourceType.toLowerCase();

        // Checked FIRST and deliberately: "confidential-encounters" contains "encounter", so the
        // ordinary clinical branch below would otherwise claim it and downgrade it to FULL_CLINICAL.
        for (String marker : PROTECTED_LANE_MARKERS) {
            if (r.contains(marker)) {
                return DataSensitivityClass.SPECIALLY_PROTECTED;
            }
        }

        if (r.contains("patient") || r.equals("patients")
                || r.contains("encounter") || r.contains("observation")
                || r.contains("diagnostic") || r.contains("medication")) {
            return DataSensitivityClass.FULL_CLINICAL;
        }
        if (r.contains("client") || r.contains("identity") || r.contains("wallet")
                || r.contains("biometric") || r.contains("card")) {
            return DataSensitivityClass.IDENTIFIED_OPERATIONAL;
        }
        if (r.contains("provider") || r.contains("practitioner")) {
            return DataSensitivityClass.IDENTIFIED_OPERATIONAL;
        }
        if (r.contains("facility") || r.contains("site")) {
            return DataSensitivityClass.IDENTIFIED_OPERATIONAL;
        }
        if (r.contains("counter") || r.contains("signal") || r.contains("aggregate")
                || r.contains("dashboard") || r.contains("report")) {
            return DataSensitivityClass.AGGREGATE_SENSITIVE;
        }
        return DataSensitivityClass.NON_SENSITIVE_OPERATIONAL;
    }

    /** Maximum disclosure tier allowed for this resource regardless of escalation. */
    public static DataVisibilityTier maxTierForResource(String resourceType) {
        DataSensitivityClass c = classifyResource(resourceType);
        return switch (c) {
            case NON_SENSITIVE_OPERATIONAL -> DataVisibilityTier.IDENTIFIED_OPERATIONAL_ONLY;
            case AGGREGATE_SENSITIVE -> DataVisibilityTier.PSEUDONYMISED_PERSON_LEVEL;
            case DEIDENTIFIED_PERSON_LEVEL -> DataVisibilityTier.DEIDENTIFIED_ROW_LEVEL;
            case PSEUDONYMISED_PERSON_LEVEL -> DataVisibilityTier.PSEUDONYMISED_PERSON_LEVEL;
            case IDENTIFIED_OPERATIONAL -> DataVisibilityTier.IDENTIFIED_OPERATIONAL_ONLY;
            case IDENTIFIED_CLINICAL_SUMMARY -> DataVisibilityTier.IDENTIFIED_LIMITED_CLINICAL;
            // Both map to the top tier because confidentiality is NOT a disclosure level — a tier
            // is a total order, and "may see the safeguarding note" does not imply "may see
            // everything below it". The confidential axis is the confidentialCategories obligation
            // on VisibilityProfile; this switch only bounds how much ordinary detail may flow.
            case FULL_CLINICAL, SPECIALLY_PROTECTED -> DataVisibilityTier.FULL_IDENTIFIED_CLINICAL;
        };
    }
}
