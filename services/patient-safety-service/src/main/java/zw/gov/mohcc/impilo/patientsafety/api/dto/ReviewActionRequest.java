package zw.gov.mohcc.impilo.patientsafety.api.dto;

/**
 * A reviewer note / status change / causality assessment on a case. {@code causalityAssessment} uses
 * the WHO-UMC scale (CERTAIN, PROBABLE, POSSIBLE, UNLIKELY, CONDITIONAL, UNASSESSABLE).
 */
public record ReviewActionRequest(
        String actionType,          // REVIEW_NOTE | STATUS_CHANGE | CAUSALITY_ASSESSED | ESCALATED (default REVIEW_NOTE)
        String toStatus,            // optional case status to move to
        String causalityAssessment, // optional WHO-UMC causality
        String mcazStatus,
        String note
) {}
