package zw.gov.mohcc.impilo.rito.domain;

import java.util.Map;
import java.util.Set;

/**
 * Classification helpers: maps a case type to its quality pillar, identifies the
 * sensitive categories whose client/provider identity must be protected, and the
 * case types that represent a clinical-service safety incident.
 */
public final class CaseClassification {

    private CaseClassification() {
    }

    // --- Case types (the eleven Rito owns) ---
    public static final String COMPLAINT = "COMPLAINT";
    public static final String COMPLIMENT = "COMPLIMENT";
    public static final String SUGGESTION = "SUGGESTION";
    public static final String SAFETY_CONCERN = "SAFETY_CONCERN";
    public static final String SAFETY_INCIDENT = "SAFETY_INCIDENT";
    public static final String NEAR_MISS = "NEAR_MISS";
    public static final String ADVERSE_EVENT = "ADVERSE_EVENT";
    public static final String QUALITY_FINDING = "QUALITY_FINDING";
    public static final String AUDIT_FINDING = "AUDIT_FINDING";
    public static final String SYSTEM_QUALITY_SIGNAL = "SYSTEM_QUALITY_SIGNAL";
    public static final String SATISFACTION_SURVEY = "SATISFACTION_SURVEY";

    public static final Set<String> CASE_TYPES = Set.of(
            COMPLAINT, COMPLIMENT, SUGGESTION, SAFETY_CONCERN, SAFETY_INCIDENT, NEAR_MISS,
            ADVERSE_EVENT, QUALITY_FINDING, AUDIT_FINDING, SYSTEM_QUALITY_SIGNAL, SATISFACTION_SURVEY);

    // --- Pillars ---
    public static final String CLIENT_VOICE = "CLIENT_VOICE";
    public static final String CLIENT_SAFETY = "CLIENT_SAFETY";
    public static final String SERVICE_QUALITY = "SERVICE_QUALITY";
    public static final String QUALITY_ASSURANCE = "QUALITY_ASSURANCE";
    public static final String ACCOUNTABLE_IMPROVEMENT = "ACCOUNTABLE_IMPROVEMENT";

    // --- Severity ---
    public static final Set<String> SEVERITIES = Set.of("LOW", "MODERATE", "HIGH", "CRITICAL");

    // --- Sources ---
    public static final Set<String> SOURCES = Set.of(
            "NOMPILO", "KHULUMA", "FACILITY_QR", "WEB_PORTAL", "MOBILE_APP", "PROVIDER_WORKSPACE",
            "FACILITY_MODE", "SUPERVISORY_VISIT", "SYSTEM_SIGNAL", "INTEGRATION_API");

    // --- Link types ---
    public static final Set<String> LINK_TYPES = Set.of(
            "CLIENT", "HEALTH_ID", "VISIT", "ENCOUNTER", "ORDER", "LAB_RESULT", "RADIOLOGY_STUDY",
            "MEDICATION", "PROVIDER", "CADRE", "FACILITY", "PUBLIC_HEALTH_SITE", "PROGRAMME",
            "DISTRICT", "PROVINCE", "ORGANISATION", "REGULATOR", "PAYMENT", "BILLING_CLAIM",
            "LEARNING_ASSIGNMENT");

    /**
     * Sensitive categories: when a case carries one of these, client and provider identity
     * are protected from non-privileged viewers unless policy explicitly allows.
     */
    public static final Set<String> SENSITIVE_CATEGORIES = Set.of(
            "SAFETY_INCIDENT", "ADVERSE_EVENT", "SAFEGUARDING", "PRIVACY_CONFIDENTIALITY",
            "PROVIDER_CONDUCT", "DISCRIMINATION_DIGNITY", "BILLING_DISPUTE");

    private static final Set<String> SAFETY_TYPES = Set.of(
            SAFETY_CONCERN, SAFETY_INCIDENT, NEAR_MISS, ADVERSE_EVENT);

    private static final Map<String, String> TYPE_TO_PILLAR = Map.ofEntries(
            Map.entry(COMPLAINT, CLIENT_VOICE),
            Map.entry(COMPLIMENT, CLIENT_VOICE),
            Map.entry(SUGGESTION, CLIENT_VOICE),
            Map.entry(SATISFACTION_SURVEY, CLIENT_VOICE),
            Map.entry(SAFETY_CONCERN, CLIENT_SAFETY),
            Map.entry(SAFETY_INCIDENT, CLIENT_SAFETY),
            Map.entry(NEAR_MISS, CLIENT_SAFETY),
            Map.entry(ADVERSE_EVENT, CLIENT_SAFETY),
            Map.entry(QUALITY_FINDING, SERVICE_QUALITY),
            Map.entry(SYSTEM_QUALITY_SIGNAL, SERVICE_QUALITY),
            Map.entry(AUDIT_FINDING, QUALITY_ASSURANCE));

    public static String pillarFor(String caseType) {
        return TYPE_TO_PILLAR.getOrDefault(caseType, SERVICE_QUALITY);
    }

    public static boolean isSafetyType(String caseType) {
        return SAFETY_TYPES.contains(caseType);
    }

    public static boolean isSensitive(String caseType, String category) {
        if (caseType != null && (SAFETY_INCIDENT.equals(caseType) || ADVERSE_EVENT.equals(caseType))) {
            return true;
        }
        return category != null && SENSITIVE_CATEGORIES.contains(category);
    }
}
