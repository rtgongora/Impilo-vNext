package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request to record a triage assessment for a patient journey.
 *
 * <p>Captures the clinical acuity level (1-5 scale), vital signs as
 * a JSON string, and any triage notes from the assessing clinician.</p>
 *
 * @param acuity the clinical acuity level (1=resuscitation, 5=non-urgent)
 * @param vitals vital signs data as a JSON string
 * @param notes  free-text triage notes
 */
public record TriageRequest(
        @Min(1) @Max(5) int acuity,
        String vitals,
        String notes
) {}
