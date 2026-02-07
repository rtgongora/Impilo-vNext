package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request to submit a batch of offline actions for reconciliation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReconciliationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID facilityId,
        @NotBlank String submittedBy,
        @NotEmpty List<OfflineActionRequest> actions
) {}
