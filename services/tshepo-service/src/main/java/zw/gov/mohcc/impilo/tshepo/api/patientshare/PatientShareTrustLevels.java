package zw.gov.mohcc.impilo.tshepo.api.patientshare;

import java.util.Locale;

/**
 * Progressive trust labels for external / provisional providers (ordinal ordering for policy gates).
 */
public final class PatientShareTrustLevels {

    public static final String SELF_ASSERTED = "SELF_ASSERTED";
    public static final String COUNCIL_NUMBER_FORMAT_VALID = "COUNCIL_NUMBER_FORMAT_VALID";
    public static final String COUNCIL_NUMBER_FOUND = "COUNCIL_NUMBER_FOUND";
    public static final String COUNCIL_AND_IDENTITY_MATCH = "COUNCIL_AND_IDENTITY_MATCH";
    public static final String FULLY_LINKED_OR_ONBOARDED = "FULLY_LINKED_OR_ONBOARDED";

    private PatientShareTrustLevels() {}

    /** Higher means stronger trust. Unknown / blank → 0. */
    public static int rank(String trustLevel) {
        if (trustLevel == null || trustLevel.isBlank()) {
            return 0;
        }
        String t = trustLevel.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case SELF_ASSERTED -> 10;
            case COUNCIL_NUMBER_FORMAT_VALID -> 20;
            case COUNCIL_NUMBER_FOUND -> 30;
            case COUNCIL_AND_IDENTITY_MATCH -> 40;
            case FULLY_LINKED_OR_ONBOARDED -> 50;
            default -> 0;
        };
    }

    public static boolean meetsMinimum(String actualTrust, String requiredTrust) {
        if (requiredTrust == null || requiredTrust.isBlank() || "*".equals(requiredTrust.trim())) {
            return true;
        }
        return rank(actualTrust) >= rank(requiredTrust);
    }
}
