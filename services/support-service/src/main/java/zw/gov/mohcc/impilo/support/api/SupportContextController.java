package zw.gov.mohcc.impilo.support.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.support.api.dto.SupportContextDtos;
import zw.gov.mohcc.impilo.support.core.SupportContextService;
import zw.gov.mohcc.impilo.support.domain.SupportContextAssignmentEntity;
import zw.gov.mohcc.impilo.support.domain.SupportElevatedAccessGrantEntity;
import zw.gov.mohcc.impilo.support.domain.SupportPermittedActionEntity;
import zw.gov.mohcc.impilo.support.domain.SupportTeamEntity;

import java.util.List;
import java.util.UUID;

/**
 * Scoped technical/operational support context administration API (A3).
 * "Support access does not imply clinical access" is enforced by shape here:
 * this controller exposes no clinical read — see /elevations for the one
 * exceptional, case-linked, time-bound, approved path beyond that boundary.
 */
@RestController
@RequestMapping("/internal/v1/support/context")
public class SupportContextController {

    private static final Logger log = LoggerFactory.getLogger(SupportContextController.class);
    private final SupportContextService service;

    public SupportContextController(SupportContextService service) {
        this.service = service;
    }

    @PostMapping("/teams")
    public ResponseEntity<?> createTeam(@Valid @RequestBody SupportContextDtos.CreateTeamRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        SupportTeamEntity draft = new SupportTeamEntity(UUID.randomUUID(), tenantId, req.teamCode(), req.name());
        if (req.supportTier() != null) draft.setSupportTier(req.supportTier());
        draft.setSupportedService(req.supportedService());
        draft.setGeographicScope(req.geographicScope());
        draft.setFacilityScopeRef(req.facilityScopeRef());
        draft.setOrganisationScope(req.organisationScope());
        draft.setEnvironmentScope(req.environmentScope());
        try {
            SupportTeamEntity saved = service.createTeam(tenantId, ctx.podId(), ctx.correlationId(), draft);
            return ResponseEntity.status(HttpStatus.CREATED).body(toTeamResponse(saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorEnvelope.of("CONFLICT", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/teams")
    public ResponseEntity<?> listTeams() {
        RequestContext ctx = RequestContextHolder.require();
        List<SupportContextDtos.TeamResponse> out = service.listTeams(UUID.fromString(ctx.tenantId())).stream()
                .map(SupportContextController::toTeamResponse).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/assignments")
    public ResponseEntity<?> createAssignment(@Valid @RequestBody SupportContextDtos.CreateAssignmentRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            SupportContextAssignmentEntity a = service.createAssignment(
                    UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(),
                    UUID.fromString(req.teamId()), req.personHealthId(), req.supportRole(), req.onCall(),
                    ctx.requestId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toAssignmentResponse(a));
        } catch (SupportContextService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_REQUEST", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @PostMapping("/assignments/{assignmentId}/end")
    public ResponseEntity<?> endAssignment(@PathVariable UUID assignmentId) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            SupportContextAssignmentEntity a = service.endAssignment(
                    assignmentId, UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), ctx.requestId());
            return ResponseEntity.ok(toAssignmentResponse(a));
        } catch (SupportContextService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/assignments/by-person/{personHealthId}")
    public ResponseEntity<?> assignmentsForPerson(@PathVariable String personHealthId) {
        List<SupportContextDtos.AssignmentResponse> out = service.assignmentsForPerson(personHealthId).stream()
                .map(SupportContextController::toAssignmentResponse).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/teams/{teamId}/permitted-actions")
    public ResponseEntity<?> addPermittedAction(@PathVariable UUID teamId,
                                                 @Valid @RequestBody SupportContextDtos.PermittedActionRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        SupportPermittedActionEntity a = service.addPermittedAction(UUID.fromString(ctx.tenantId()), teamId, req.actionCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SupportContextDtos.PermittedActionResponse(a.getId().toString(), a.getTeamId().toString(), a.getActionCode()));
    }

    @GetMapping("/teams/{teamId}/permitted-actions")
    public ResponseEntity<?> permittedActions(@PathVariable UUID teamId) {
        List<SupportContextDtos.PermittedActionResponse> out = service.permittedActions(teamId).stream()
                .map(a -> new SupportContextDtos.PermittedActionResponse(a.getId().toString(), a.getTeamId().toString(), a.getActionCode()))
                .toList();
        return ResponseEntity.ok(out);
    }

    // ---- Elevated access: the exceptional, case-linked, time-bound path -----

    @PostMapping("/elevations")
    public ResponseEntity<?> requestElevation(@Valid @RequestBody SupportContextDtos.RequestElevationRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            SupportElevatedAccessGrantEntity grant = service.requestElevation(
                    UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(),
                    UUID.fromString(req.supportAssignmentId()), UUID.fromString(req.ticketId()),
                    req.statedPurpose(), req.minimumScope(), req.requestedBy());
            return ResponseEntity.status(HttpStatus.CREATED).body(toGrantResponse(grant));
        } catch (SupportContextService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        } catch (SupportContextService.InvalidElevationStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorEnvelope.of("INVALID_STATE", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @PostMapping("/elevations/{grantId}/approve")
    public ResponseEntity<?> approveElevation(@PathVariable UUID grantId,
                                               @Valid @RequestBody SupportContextDtos.ApproveElevationRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            SupportElevatedAccessGrantEntity grant = service.approveElevation(
                    grantId, UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(),
                    req.approvedBy(), req.durationMinutes());
            return ResponseEntity.ok(toGrantResponse(grant));
        } catch (SupportContextService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        } catch (SupportContextService.InvalidElevationStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorEnvelope.of("INVALID_STATE", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @PostMapping("/elevations/{grantId}/revoke")
    public ResponseEntity<?> revokeElevation(@PathVariable UUID grantId,
                                              @Valid @RequestBody SupportContextDtos.RevokeElevationRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            SupportElevatedAccessGrantEntity grant = service.revokeElevation(
                    grantId, UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(),
                    req.revokedBy(), req.reason());
            return ResponseEntity.ok(toGrantResponse(grant));
        } catch (SupportContextService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        } catch (SupportContextService.InvalidElevationStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorEnvelope.of("INVALID_STATE", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/assignments/{assignmentId}/elevations/active")
    public ResponseEntity<?> activeElevations(@PathVariable UUID assignmentId) {
        List<SupportContextDtos.ElevationGrantResponse> out = service.activeElevations(assignmentId).stream()
                .map(SupportContextController::toGrantResponse).toList();
        return ResponseEntity.ok(out);
    }

    private static SupportContextDtos.TeamResponse toTeamResponse(SupportTeamEntity t) {
        return new SupportContextDtos.TeamResponse(
                t.getTeamId().toString(), t.getTeamCode(), t.getName(), t.getSupportTier(),
                t.getSupportedService(), t.getGeographicScope(), t.getFacilityScopeRef(),
                t.getOrganisationScope(), t.getEnvironmentScope(), t.isActive());
    }

    private static SupportContextDtos.AssignmentResponse toAssignmentResponse(SupportContextAssignmentEntity a) {
        return new SupportContextDtos.AssignmentResponse(
                a.getAssignmentId().toString(), a.getTeamId().toString(), a.getPersonHealthId(), a.getSupportRole(),
                a.isOnCall(), a.getStatus(), String.valueOf(a.getStartDate()),
                a.getEndDate() != null ? a.getEndDate().toString() : null);
    }

    private static SupportContextDtos.ElevationGrantResponse toGrantResponse(SupportElevatedAccessGrantEntity g) {
        return new SupportContextDtos.ElevationGrantResponse(
                g.getGrantId().toString(), g.getSupportAssignmentId().toString(), g.getTicketId().toString(),
                g.getStatedPurpose(), g.getMinimumScope(), g.getRequestedBy(), g.getApprovedBy(), g.getStatus(),
                g.getGrantedAt() != null ? g.getGrantedAt().toString() : null,
                g.getExpiresAt() != null ? g.getExpiresAt().toString() : null,
                g.getRevokedAt() != null ? g.getRevokedAt().toString() : null,
                g.isVisibleIndicator(), g.isCurrentlyActive());
    }
}
