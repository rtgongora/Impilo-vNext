package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.QueueItemStatusRequest;
import zw.gov.mohcc.impilo.pct.api.dto.TransferQueueItemRequest;
import zw.gov.mohcc.impilo.pct.core.QueueEngine;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

/**
 * REST controller for individual queue item operations.
 *
 * <p>Provides endpoints for updating queue item status (e.g. marking
 * a patient as IN_SERVICE or COMPLETED) and transferring items between
 * queues.</p>
 */
@RestController
@RequestMapping("/v1/queue-items")
public class QueueItemController {

    private static final Logger log = LoggerFactory.getLogger(QueueItemController.class);

    private final QueueEngine queueEngine;

    public QueueItemController(QueueEngine queueEngine) {
        this.queueEngine = queueEngine;
    }

    /**
     * Update the status of a queue item.
     *
     * @param itemId  the queue item identifier
     * @param request the status update request
     * @return the updated queue item
     */
    @PostMapping("/{itemId}/status")
    public ResponseEntity<ApiResponse<QueueItemEntity>> updateStatus(
            @PathVariable UUID itemId,
            @Valid @RequestBody QueueItemStatusRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        QueueItemStatus newStatus = QueueItemStatus.valueOf(request.status().toUpperCase());
        QueueItemEntity item = queueEngine.updateItemStatus(itemId, newStatus);

        return ResponseEntity.ok(ApiResponse.ok(item, correlationId));
    }

    /**
     * Transfer a queue item to a different queue.
     *
     * @param itemId  the queue item to transfer
     * @param request the transfer request containing the target queue
     * @return the newly created queue item in the target queue
     */
    @PostMapping("/{itemId}/transfer")
    public ResponseEntity<ApiResponse<QueueItemEntity>> transferItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody TransferQueueItemRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        QueueItemEntity newItem = queueEngine.transferItem(itemId, request.targetQueueId());

        return ResponseEntity.ok(ApiResponse.ok(newItem, correlationId));
    }
}
