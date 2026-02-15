package zw.gov.mohcc.impilo.integration.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.integration.api.dto.DeadLetterResponse;
import zw.gov.mohcc.impilo.integration.domain.DeadLetterEntity;
import zw.gov.mohcc.impilo.integration.repository.DeadLetterRepository;

@RestController
@RequestMapping("/internal/v1/deadletters")
public class DeadLetterController {

    private final DeadLetterRepository deadLetterRepository;

    public DeadLetterController(DeadLetterRepository deadLetterRepository) {
        this.deadLetterRepository = deadLetterRepository;
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
