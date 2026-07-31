package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LostDeviceRecoveryDtos {
    private LostDeviceRecoveryDtos() {}
    public record CreateRequest(@NotBlank String targetUserId, @NotBlank String reason,
            @NotEmpty Map<String,Object> identityProofEvidence,
            @NotEmpty List<String> selectedCredentialIds) {}
    public record ApprovalRequest(boolean approved, String notes) {}
    public record ExecutionRequest(boolean succeeded, String executionReference, String error) {}
    public record View(UUID id, UUID tenantId, String targetUserId, String requesterUserId,
            String approverUserId, String status, String reason, Map<String,Object> identityProofEvidence,
            List<String> selectedCredentialIds, Instant requestedAt, Instant approvedAt,
            Instant executedAt, String executionReference, String executionError) {}
}
