package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.EnqueueRequest;
import zw.gov.mohcc.impilo.pct.core.QueueEngine;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.*;

/**
 * REST controller for queue management operations.
 *
 * <p>Provides endpoints for listing queues with stats, enqueueing patients,
 * calling the next patient, and viewing queue items. All operations are
 * scoped to the current tenant via the trust context.</p>
 */
@RestController
@RequestMapping("/v1/queues")
public class QueueController {

    private static final Logger log = LoggerFactory.getLogger(QueueController.class);

    private final QueueEngine queueEngine;
    private final QueueRepository queueRepository;
    private final QueueItemRepository queueItemRepository;

    public QueueController(QueueEngine queueEngine,
                           QueueRepository queueRepository,
                           QueueItemRepository queueItemRepository) {
        this.queueEngine = queueEngine;
        this.queueRepository = queueRepository;
        this.queueItemRepository = queueItemRepository;
    }

    /**
     * List queues with real-time stats for a facility, optionally filtered by workspace.
     *
     * @param facilityId  the facility to query (required)
     * @param workspaceId optional workspace filter
     * @return list of queues with stats
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getQueues(
            @RequestParam UUID facilityId,
            @RequestParam(required = false) UUID workspaceId) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        List<QueueEntity> queues;
        if (workspaceId != null) {
            queues = queueRepository.findByTenantIdAndWorkspaceIdAndActiveTrue(ctx.tenantId(), workspaceId);
        } else {
            queues = queueRepository.findByTenantIdAndFacilityId(ctx.tenantId(), facilityId);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (QueueEntity queue : queues) {
            Map<String, Object> queueWithStats = new LinkedHashMap<>();
            queueWithStats.put("queueId", queue.getQueueId());
            queueWithStats.put("name", queue.getName());
            queueWithStats.put("queueType", queue.getQueueType());
            queueWithStats.put("active", queue.isActive());

            Map<String, Object> stats = queueEngine.getQueueStats(queue.getQueueId());
            queueWithStats.putAll(stats);

            result.add(queueWithStats);
        }

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * Enqueue a patient journey into a specific queue.
     *
     * @param queueId the target queue
     * @param request the enqueue request containing journey ID and priority
     * @return the created queue item
     */
    @PostMapping("/{queueId}/enqueue")
    public ResponseEntity<ApiResponse<QueueItemEntity>> enqueue(
            @PathVariable UUID queueId,
            @Valid @RequestBody EnqueueRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        QueueItemEntity item = queueEngine.enqueue(queueId, request.journeyId(), request.priority());

        return ResponseEntity.ok(ApiResponse.ok(item, correlationId));
    }

    /**
     * Call the next patient from a queue.
     *
     * @param queueId the queue to call from
     * @return the called queue item, or 204 if no waiting items
     */
    @PostMapping("/{queueId}/call-next")
    public ResponseEntity<ApiResponse<QueueItemEntity>> callNext(@PathVariable UUID queueId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        QueueItemEntity item = queueEngine.callNext(queueId);
        if (item == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(ApiResponse.ok(item, correlationId));
    }

    /**
     * List queue items filtered by status.
     *
     * @param queueId the queue to query
     * @param status  optional status filter (e.g. WAITING, CALLED, IN_SERVICE)
     * @return list of queue items ordered by priority and creation time
     */
    @GetMapping("/{queueId}/items")
    public ResponseEntity<ApiResponse<List<QueueItemEntity>>> getQueueItems(
            @PathVariable UUID queueId,
            @RequestParam(required = false) String status) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        List<QueueItemEntity> items;
        if (status != null && !status.isBlank()) {
            items = queueItemRepository
                    .findByTenantIdAndQueueIdAndStatusOrderByPriorityDescCreatedAtAsc(
                            ctx.tenantId(), queueId, status);
        } else {
            items = queueItemRepository
                    .findByTenantIdAndQueueIdAndStatusInOrderByPriorityDescCreatedAtAsc(
                            ctx.tenantId(), queueId,
                            List.of("WAITING", "CALLED", "IN_SERVICE", "PAUSED"));
        }

        return ResponseEntity.ok(ApiResponse.ok(items, correlationId));
    }
}
