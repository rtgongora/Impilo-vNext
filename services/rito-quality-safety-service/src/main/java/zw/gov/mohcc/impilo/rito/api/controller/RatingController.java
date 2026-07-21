package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.ModerationRequest;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.ProviderResponseRequest;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.RatingSummary;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.SubmitRatingRequest;
import zw.gov.mohcc.impilo.rito.core.RatingModerationService;
import zw.gov.mohcc.impilo.rito.core.RatingService;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderResponseEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.RatingModerationEntity;

import java.util.List;
import java.util.UUID;

/**
 * Provider rating submission, provider response and moderation (RW3). Internal lane —
 * fronted by the BFF; the client of record is an authenticated service principal. Rating
 * writes never touch provider authority (regulation firewall; asserted by RW7 tests).
 */
@RestController
@RequestMapping("/internal/v1/rito/ratings")
public class RatingController {

    private final RatingService ratingService;
    private final RatingModerationService moderationService;

    public RatingController(RatingService ratingService, RatingModerationService moderationService) {
        this.ratingService = ratingService;
        this.moderationService = moderationService;
    }

    @PostMapping
    public ResponseEntity<ProviderRatingEntity> submit(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody SubmitRatingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.submitRating(tenantId, request));
    }

    @PostMapping("/{ratingId}/response")
    public ResponseEntity<ProviderResponseEntity> respond(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID ratingId,
            @RequestBody ProviderResponseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.respond(tenantId, ratingId, request));
    }

    /** Ratings attributed to one provider — provider self-view + response surface (RW13). */
    @GetMapping("/provider/{providerPublicId}")
    public ResponseEntity<List<RatingSummary>> listForProvider(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String providerPublicId) {
        return ResponseEntity.ok(ratingService.listForProvider(tenantId, providerPublicId));
    }

    /** Moderation queue — ratings pending review (RW14). Defaults to SUBMITTED + UNDER_REVIEW. */
    @GetMapping("/queue")
    public ResponseEntity<List<RatingSummary>> moderationQueue(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) String state) {
        return ResponseEntity.ok(ratingService.listByModerationState(tenantId, state));
    }

    @PostMapping("/{ratingId}/moderate")
    public ResponseEntity<RatingModerationEntity> moderate(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID ratingId,
            @RequestBody ModerationRequest request) {
        return ResponseEntity.ok(moderationService.moderate(
                tenantId, actorId, ratingId, request.decision(), request.reason(), request.manipulationFlags()));
    }
}
