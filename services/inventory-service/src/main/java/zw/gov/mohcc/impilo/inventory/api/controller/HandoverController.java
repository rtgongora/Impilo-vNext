package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.StartHandoverRequest;
import zw.gov.mohcc.impilo.inventory.core.HandoverService;
import zw.gov.mohcc.impilo.inventory.domain.HandoverStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.HandoverEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for stock custody handover management.
 *
 * <p>Manages shift handovers between actors: initiate, sign outgoing,
 * sign incoming (completing the handover), and list handovers.</p>
 */
@RestController
@RequestMapping("/v1/handover")
public class HandoverController {

    private static final Logger log = LoggerFactory.getLogger(HandoverController.class);

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /**
     * Initiate a new handover for a store.
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<HandoverSummary>> startHandover(
            @Valid @RequestBody StartHandoverRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        HandoverEntity entity = handoverService.startHandover(request.storeId(), request.toActor());

        log.info("Handover initiated: handoverId={}, storeId={}, toActor={}",
                entity.getHandoverId(), request.storeId(), request.toActor());

        return ResponseEntity.ok(ApiResponse.ok(HandoverSummary.from(entity), correlationId));
    }

    /**
     * Record the outgoing actor's signature.
     */
    @PostMapping("/{id}/sign-outgoing")
    public ResponseEntity<ApiResponse<HandoverSummary>> signOutgoing(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        HandoverEntity entity = handoverService.signOutgoing(id);

        log.info("Handover outgoing signed: handoverId={}", id);

        return ResponseEntity.ok(ApiResponse.ok(HandoverSummary.from(entity), correlationId));
    }

    /**
     * Record the incoming actor's signature (completes the handover).
     */
    @PostMapping("/{id}/sign-incoming")
    public ResponseEntity<ApiResponse<HandoverSummary>> signIncoming(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        HandoverEntity entity = handoverService.signIncoming(id);

        log.info("Handover incoming signed: handoverId={}, status={}", id, entity.getStatus());

        return ResponseEntity.ok(ApiResponse.ok(HandoverSummary.from(entity), correlationId));
    }

    /**
     * List handovers for the current tenant (paginated).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<HandoverSummary>>> getHandovers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<HandoverEntity> result = handoverService.getHandovers(PageRequest.of(page, size));

        List<HandoverSummary> items = result.getContent().stream()
                .map(HandoverSummary::from)
                .collect(Collectors.toList());

        PagedResponse<HandoverSummary> pagedResponse = PagedResponse.of(
                items, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, correlationId));
    }

    /**
     * Inline summary record for handover responses.
     */
    private record HandoverSummary(
            UUID handoverId,
            UUID storeId,
            String fromActor,
            String toActor,
            HandoverStatus status,
            OffsetDateTime outgoingSignedAt,
            OffsetDateTime incomingSignedAt,
            OffsetDateTime createdAt
    ) {
        static HandoverSummary from(HandoverEntity entity) {
            return new HandoverSummary(
                    entity.getHandoverId(),
                    entity.getStoreId(),
                    entity.getFromActor(),
                    entity.getToActor(),
                    entity.getStatus(),
                    entity.getOutgoingSignedAt(),
                    entity.getIncomingSignedAt(),
                    entity.getCreatedAt());
        }
    }
}
