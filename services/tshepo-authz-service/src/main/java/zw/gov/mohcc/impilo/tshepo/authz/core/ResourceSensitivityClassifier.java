package zw.gov.mohcc.impilo.tshepo.authz.core;

import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataSensitivityClass;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataVisibilityTier;

/**
 * Maps resource types (from URL segments / resource registry) to a coarse sensitivity class
 * and the maximum visibility tier this resource type may ever receive.
 */
public final class ResourceSensitivityClassifier {

    private ResourceSensitivityClassifier() {
    }

    public static DataSensitivityClass classifyResource(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return DataSensitivityClass.NON_SENSITIVE_OPERATIONAL;
        }
        String r = resourceType.toLowerCase();

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
            case FULL_CLINICAL, SPECIALLY_PROTECTED -> DataVisibilityTier.FULL_IDENTIFIED_CLINICAL;
        };
    }
}
