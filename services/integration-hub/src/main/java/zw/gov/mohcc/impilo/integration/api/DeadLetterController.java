package zw.gov.mohcc.impilo.integration.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.integration.api.dto.DeadLetterResponse;
import zw.gov.mohcc.impilo.integration.api.dto.DispatchRequest;
import zw.gov.mohcc.impilo.integration.api.dto.DispatchResponse;
import zw.gov.mohcc.impilo.integration.domain.DeadLetterEntity;
import zw.gov.mohcc.impilo.integration.repository.DeadLetterRepository;
import zw.gov.mohcc.impilo.integration.service.DispatchService;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/internal/v1/deadletters")
public class DeadLetterController {

    private final DeadLetterRepository deadLetterRepository;
    private final DispatchService dispatchService;

    public DeadLetterController(DeadLetterRepository deadLetterRepository,
                                DispatchService dispatchService) {
        this.deadLetterRepository = deadLetterRepository;
        this.dispatchService = dispatchService;
    }

    @GetMapping
    public ResponseEntity<Page<DeadLetterResponse>> listDeadLetters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean resolved) {

        RequestContext ctx = RequestContextHolder.require();
        Pageable pageable = PageRequest.of(page, size);

        Page<DeadLetterEntity> entities;
        if (resolved != null) {
            entities = deadLetterRepository.findByTenantIdAndResolvedOrderByCreatedAtDesc(
                    ctx.tenantId(), resolved, pageable);
        } else {
            entities = deadLetterRepository.findByTenantIdOrderByCreatedAtDesc(
                    ctx.tenantId(), pageable);
        }

        Page<DeadLetterResponse> responses = entities.map(this::toResponse);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<DispatchResponse> replayDeadLetter(@PathVariable String id) {
        RequestContext ctx = RequestContextHolder.require();

        DeadLetterEntity deadLetter = deadLetterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dead letter not found: " + id));

        if (deadLetter.isResolved()) {
            throw new IllegalStateException("Dead letter already resolved: " + id);
        }

        // Increment retry count and update last retry timestamp
        deadLetter.setRetryCount(deadLetter.getRetryCount() + 1);
        deadLetter.setLastRetryAt(OffsetDateTime.now());
        deadLetterRepository.save(deadLetter);

        // Re-dispatch using the original request data
        DispatchRequest replayRequest = new DispatchRequest(
                deadLetter.getMethod(),
                deadLetter.getPath(),
                deadLetter.getRequestBody(),
                null
        );

        DispatchResponse response = dispatchService.dispatch(replayRequest, ctx);

        // If the replay dispatch succeeded (matched a route), mark as resolved
        if ("ACCEPTED".equals(response.status()) || "DISPATCHED".equals(response.status())) {
            deadLetter.setResolved(true);
            deadLetterRepository.save(deadLetter);
        }

        return ResponseEntity.ok(response);
    }

    private DeadLetterResponse toResponse(DeadLetterEntity entity) {
        return new DeadLetterResponse(
                entity.getId(),
                entity.getDispatchAttemptId(),
                entity.getRouteId(),
                entity.getMethod(),
                entity.getPath(),
                entity.getErrorReason(),
                entity.getRetryCount(),
                entity.getLastRetryAt(),
                entity.isResolved(),
                entity.getCreatedAt()
        );
    }
}
