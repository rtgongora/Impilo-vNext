package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * OF-B19 §12.6 — pharmacist stability decision on an excursion-flagged
 * cold-chain delivery: {@code USE} (excursion inside the product's stability
 * budget — handover unblocked) or {@code QUARANTINE} (cargo returns per §12.7,
 * refund seam fires via the msika-flow write-back).
 */
public record StabilityDecisionRequest(
        @NotBlank @JsonProperty("decision") String decision,
        @JsonProperty("notes") String notes
) {}
