package zw.gov.mohcc.impilo.abis.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request bodies for the adjudication console actions.
 */
public final class AdjudicationRequests {

    private AdjudicationRequests() {}

    public record AssignRequest(@NotBlank @Size(max = 128) String reviewer) {}

    public record DecisionRequest(
            @NotBlank String decision,
            @Size(max = 2000) String reason) {}

    public record MergeRequest(@NotBlank @Size(max = 128) String mergeTargetSubjectRef) {}
}
