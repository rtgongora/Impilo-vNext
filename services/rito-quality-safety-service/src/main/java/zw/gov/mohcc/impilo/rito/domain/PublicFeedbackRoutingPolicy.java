package zw.gov.mohcc.impilo.rito.domain;

import java.util.Map;
import java.util.Set;

/**
 * Routing policy for the public feedback loop ("Did this work?" / anonymous feedback form).
 *
 * <p>A citizen-facing {@code feedbackCategory} is a granular topic distinct from the clinical
 * {@link CaseClassification} case type. Each category maps to a triple:
 * <ol>
 *   <li>a canonical Rito {@code caseType} (Rito remains the system of record for cases);</li>
 *   <li>an {@code operationalOwner} — the team whose governed correction / support queue must
 *       receive the report (metadata that drives the routed outbox event, NOT a new case store);</li>
 *   <li>a {@code triagePriority} — CRITICAL / HIGH / ROUTINE — driving escalation and SLA.</li>
 * </ol>
 *
 * <p>This is data-driven on purpose: adding a category is one {@link #ROUTING} entry. Categories
 * whose report is about another team's data (Tuso/Varapi/Ndila/Nhume) carry that team as the
 * operational owner so the report enters that team's correction queue; privacy/security and
 * patient-safety escalate to CRITICAL.</p>
 */
public final class PublicFeedbackRoutingPolicy {

    private PublicFeedbackRoutingPolicy() {
    }

    // ── Triage priority vocabulary (distinct from case severity LOW/MODERATE/HIGH/CRITICAL) ──
    public static final String CRITICAL = "CRITICAL";
    public static final String HIGH = "HIGH";
    public static final String ROUTINE = "ROUTINE";

    // ── Operational owners (stable slugs; consumed by each team's governed queue) ──
    public static final String OWNER_DIGITAL_SUPPORT = "digital-support";
    public static final String OWNER_ENGINEERING = "engineering";
    public static final String OWNER_PCT = "pct";                          // programme/care-team: booking + telemedicine
    public static final String OWNER_TUSO_DATA_STEWARD = "tuso-data-steward";   // facility master data
    public static final String OWNER_VARAPI_DATA_STEWARD = "varapi-data-steward"; // provider registry data
    public static final String OWNER_NDILA = "ndila";                     // mapping / directions
    public static final String OWNER_NHUME = "nhume";                     // transport / dispatch
    public static final String OWNER_COSTA_MUSHEX = "costa-mushex";       // payments / wallet
    public static final String OWNER_VITO_TSHEPO = "vito-tshepo";         // identity / access / Health ID
    public static final String OWNER_DATA_PROTECTION = "data-protection"; // privacy / security office
    public static final String OWNER_PATIENT_SAFETY_QA = "patient-safety-qa";
    public static final String OWNER_FACILITY_DISTRICT_COMPLAINTS = "facility-district-complaints";
    public static final String OWNER_CLIENT_VOICE = "client-voice";

    /**
     * Routing outcome for one feedback category.
     *
     * @param caseType         canonical Rito case type the report becomes
     * @param operationalOwner team whose governed queue must receive it
     * @param triagePriority   CRITICAL / HIGH / ROUTINE
     */
    public record Routing(String caseType, String operationalOwner, String triagePriority) {
    }

    // ── Category identifiers (citizen-facing granular topics) ──
    public static final String SOMETHING_NOT_WORKING = "SOMETHING_NOT_WORKING";
    public static final String PERFORMANCE = "PERFORMANCE";
    public static final String SIGN_IN_PROBLEM = "SIGN_IN_PROBLEM";
    public static final String BOOKING_PROBLEM = "BOOKING_PROBLEM";
    public static final String TELEMEDICINE_PROBLEM = "TELEMEDICINE_PROBLEM";
    public static final String FACILITY_INFO_INCORRECT = "FACILITY_INFO_INCORRECT";
    public static final String PROVIDER_INFO_INCORRECT = "PROVIDER_INFO_INCORRECT";
    public static final String MAPPING_DIRECTIONS = "MAPPING_DIRECTIONS";
    public static final String TRANSPORT_PROBLEM = "TRANSPORT_PROBLEM";
    public static final String PAYMENT_PROBLEM = "PAYMENT_PROBLEM";
    public static final String HEALTH_ID_IDENTITY = "HEALTH_ID_IDENTITY";
    public static final String ACCESSIBILITY_LANGUAGE = "ACCESSIBILITY_LANGUAGE";
    public static final String PRIVACY_SECURITY = "PRIVACY_SECURITY";
    public static final String PATIENT_SAFETY = "PATIENT_SAFETY";
    public static final String COMPLAINT_ABOUT_CARE = "COMPLAINT_ABOUT_CARE";
    public static final String SUGGESTION = "SUGGESTION";
    public static final String COMPLIMENT = "COMPLIMENT";

    /** Category → routing. Extend by adding an entry here. */
    public static final Map<String, Routing> ROUTING = Map.ofEntries(
            // Technical / usability — engineering & digital support, routine unless access is blocked.
            Map.entry(SOMETHING_NOT_WORKING,
                    new Routing(CaseClassification.SUGGESTION, OWNER_DIGITAL_SUPPORT, ROUTINE)),
            Map.entry(PERFORMANCE,
                    new Routing(CaseClassification.SUGGESTION, OWNER_ENGINEERING, ROUTINE)),
            Map.entry(ACCESSIBILITY_LANGUAGE,
                    new Routing(CaseClassification.SUGGESTION, OWNER_DIGITAL_SUPPORT, ROUTINE)),

            // Access-to-care blocked / identity blocking care — HIGH.
            Map.entry(SIGN_IN_PROBLEM,
                    new Routing(CaseClassification.COMPLAINT, OWNER_VITO_TSHEPO, HIGH)),
            Map.entry(HEALTH_ID_IDENTITY,
                    new Routing(CaseClassification.COMPLAINT, OWNER_VITO_TSHEPO, HIGH)),
            Map.entry(BOOKING_PROBLEM,
                    new Routing(CaseClassification.COMPLAINT, OWNER_PCT, HIGH)),
            Map.entry(TELEMEDICINE_PROBLEM,
                    new Routing(CaseClassification.COMPLAINT, OWNER_PCT, HIGH)),
            Map.entry(TRANSPORT_PROBLEM,
                    new Routing(CaseClassification.COMPLAINT, OWNER_NHUME, HIGH)),

            // Cross-team data corrections — route to the owning steward's queue.
            // Facility operating status feeds care navigation → HIGH; other records → routine correction.
            Map.entry(FACILITY_INFO_INCORRECT,
                    new Routing(CaseClassification.SUGGESTION, OWNER_TUSO_DATA_STEWARD, HIGH)),
            Map.entry(PROVIDER_INFO_INCORRECT,
                    new Routing(CaseClassification.SUGGESTION, OWNER_VARAPI_DATA_STEWARD, ROUTINE)),
            Map.entry(MAPPING_DIRECTIONS,
                    new Routing(CaseClassification.SUGGESTION, OWNER_NDILA, ROUTINE)),

            // Money — a deducted-but-unfulfilled payment is CRITICAL.
            Map.entry(PAYMENT_PROBLEM,
                    new Routing(CaseClassification.COMPLAINT, OWNER_COSTA_MUSHEX, CRITICAL)),

            // Privacy / security incident and patient safety — always CRITICAL, safety pillar.
            Map.entry(PRIVACY_SECURITY,
                    new Routing(CaseClassification.SAFETY_CONCERN, OWNER_DATA_PROTECTION, CRITICAL)),
            Map.entry(PATIENT_SAFETY,
                    new Routing(CaseClassification.SAFETY_CONCERN, OWNER_PATIENT_SAFETY_QA, CRITICAL)),

            // Care experience & client voice.
            Map.entry(COMPLAINT_ABOUT_CARE,
                    new Routing(CaseClassification.COMPLAINT, OWNER_FACILITY_DISTRICT_COMPLAINTS, ROUTINE)),
            Map.entry(SUGGESTION,
                    new Routing(CaseClassification.SUGGESTION, OWNER_CLIENT_VOICE, ROUTINE)),
            Map.entry(COMPLIMENT,
                    new Routing(CaseClassification.COMPLIMENT, OWNER_CLIENT_VOICE, ROUTINE)));

    /** Every known citizen-facing feedback category. */
    public static final Set<String> CATEGORIES = ROUTING.keySet();

    /** @return the routing for a category, or {@code null} if the category is unknown. */
    public static Routing resolve(String feedbackCategory) {
        return feedbackCategory == null ? null : ROUTING.get(feedbackCategory);
    }

    public static boolean isKnownCategory(String feedbackCategory) {
        return feedbackCategory != null && ROUTING.containsKey(feedbackCategory);
    }

    /**
     * Case severity implied by a triage priority (drives {@code applySla} and the critical path).
     * ROUTINE leaves the case default severity untouched.
     *
     * @return CRITICAL / HIGH, or {@code null} to keep the default severity.
     */
    public static String severityFor(String triagePriority) {
        if (CRITICAL.equals(triagePriority)) {
            return "CRITICAL";
        }
        if (HIGH.equals(triagePriority)) {
            return "HIGH";
        }
        return null;
    }
}
