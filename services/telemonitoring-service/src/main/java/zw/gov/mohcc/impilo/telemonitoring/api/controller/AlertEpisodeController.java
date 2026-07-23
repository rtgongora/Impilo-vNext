package zw.gov.mohcc.impilo.telemonitoring.api.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.AlertEpisodeDtos.AcknowledgeRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.AlertEpisodeDtos.AssumptionOfCareRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.AlertEpisodeDtos.EpisodeResponse;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.AlertEpisodeDtos.LadderStepRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.AlertEpisodeDtos.ResolveRequest;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertEpisodeService;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertStatus;

import java.util.List;
import java.util.UUID;

/**
 * Alert-episode actions surface (OF-B26) — the API face of the §14.7 ladder and the
 * §14.6 accountable-closure law. Reached through the experience-BFF with the caller's
 * trust context; the acting identity defaults to {@code X-Actor-ID} and every action
 * demands one (no anonymous acknowledgement, escalation or closure).
 */
@RestController
public class AlertEpisodeController {

    private final AlertEpisodeService episodeService;

    public AlertEpisodeController(AlertEpisodeService episodeService) {
        this.episodeService = episodeService;
    }

    @GetMapping("/v1/alert-episodes")
    public List<EpisodeResponse> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) String deviceRef) {
        return episodeService.list(tenantId, planId, status, deviceRef).stream()
                .map(EpisodeResponse::from)
                .toList();
    }

    @GetMapping("/v1/alert-episodes/{episodeId}")
    public EpisodeResponse get(@PathVariable UUID episodeId) {
        return EpisodeResponse.from(episodeService.get(episodeId));
    }

    @PostMapping("/v1/alert-episodes/{episodeId}/acknowledge")
    public EpisodeResponse acknowledge(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID episodeId,
            @RequestBody(required = false) AcknowledgeRequest request) {
        String actor = request != null && request.acknowledgedBy() != null && !request.acknowledgedBy().isBlank()
                ? request.acknowledgedBy() : actorId;
        return EpisodeResponse.from(episodeService.acknowledge(episodeId, actor));
    }

    @PostMapping("/v1/alert-episodes/{episodeId}/start")
    public EpisodeResponse startProgress(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID episodeId) {
        return EpisodeResponse.from(episodeService.startProgress(episodeId, actorId));
    }

    @PostMapping("/v1/alert-episodes/{episodeId}/ladder-steps")
    public EpisodeResponse recordLadderStep(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID episodeId,
            @Valid @RequestBody LadderStepRequest request) {
        String actor = request.actor() != null && !request.actor().isBlank() ? request.actor() : actorId;
        return EpisodeResponse.from(
                episodeService.recordLadderStep(episodeId, request.rung(), actor, request.note()));
    }

    @PostMapping("/v1/alert-episodes/{episodeId}/assumption-of-care")
    public EpisodeResponse recordAssumptionOfCare(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID episodeId,
            @Valid @RequestBody AssumptionOfCareRequest request) {
        String actor = request.actor() != null && !request.actor().isBlank() ? request.actor() : actorId;
        return EpisodeResponse.from(
                episodeService.recordAssumptionOfCare(episodeId, request.note(), actor));
    }

    @PostMapping("/v1/alert-episodes/{episodeId}/resolve")
    public EpisodeResponse resolve(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID episodeId,
            @RequestBody(required = false) ResolveRequest request) {
        String resolvedBy = request != null && request.resolvedBy() != null && !request.resolvedBy().isBlank()
                ? request.resolvedBy() : actorId;
        return EpisodeResponse.from(episodeService.resolve(episodeId, resolvedBy,
                request != null ? request.assessment() : null,
                request != null ? request.action() : null,
                request != null ? request.outcome() : null));
    }
}
