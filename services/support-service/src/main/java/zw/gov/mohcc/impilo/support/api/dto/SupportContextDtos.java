package zw.gov.mohcc.impilo.support.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/** Support-context read/write DTOs (A3 — scoped support model). */
public final class SupportContextDtos {

    private SupportContextDtos() {}

    public record CreateTeamRequest(
            @NotBlank String teamCode,
            @NotBlank String name,
            String supportTier,
            String supportedService,
            String geographicScope,
            String facilityScopeRef,
            String organisationScope,
            String environmentScope) {}

    public record TeamResponse(
            String teamId, String teamCode, String name, String supportTier,
            String supportedService, String geographicScope, String facilityScopeRef,
            String organisationScope, String environmentScope, boolean active) {}

    public record CreateAssignmentRequest(
            @NotBlank String teamId,
            @NotBlank String personHealthId,
            String supportRole,
            boolean onCall) {}

    public record AssignmentResponse(
            String assignmentId, String teamId, String personHealthId, String supportRole,
            boolean onCall, String status, String startDate, String endDate) {}

    public record PermittedActionRequest(@NotBlank String actionCode) {}

    public record PermittedActionResponse(String id, String teamId, String actionCode) {}

    public record RequestElevationRequest(
            @NotBlank String supportAssignmentId,
            @NotBlank String ticketId,
            @NotBlank String statedPurpose,
            @NotBlank String minimumScope,
            @NotBlank String requestedBy,
            Integer maxDurationMinutes) {}

    public record ApproveElevationRequest(
            @NotBlank String approvedBy,
            @NotBlank Integer durationMinutes) {}

    public record RevokeElevationRequest(
            @NotBlank String revokedBy,
            String reason) {}

    public record ElevationGrantResponse(
            String grantId, String supportAssignmentId, String ticketId, String statedPurpose,
            String minimumScope, String requestedBy, String approvedBy, String status,
            String grantedAt, String expiresAt, String revokedAt, boolean visibleIndicator, boolean currentlyActive) {}
}
