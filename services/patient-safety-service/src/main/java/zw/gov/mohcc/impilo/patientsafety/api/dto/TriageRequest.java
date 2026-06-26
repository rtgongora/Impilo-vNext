package zw.gov.mohcc.impilo.patientsafety.api.dto;

/** MCAZ triage decision for a case. */
public record TriageRequest(
        String priority,            // ROUTINE | HIGH | URGENT (optional override)
        String assignedReviewerId,
        String mcazStatus,
        String note
) {}
