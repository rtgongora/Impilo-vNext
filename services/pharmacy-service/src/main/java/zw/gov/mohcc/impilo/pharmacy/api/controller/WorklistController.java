package zw.gov.mohcc.impilo.pharmacy.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.api.dto.DispenseOrderSummaryDto;
import zw.gov.mohcc.impilo.pharmacy.core.WorklistService;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for pharmacy worklist operations.
 *
 * <p>Provides a filtered, paginated view of dispense orders at a facility
 * with support for filtering by workspace, store, status, and priority.</p>
 */
@RestController
@RequestMapping("/v1")
public class WorklistController {

    private static final Logger log = LoggerFactory.getLogger(WorklistController.class);

    private final WorklistService worklistService;

    public WorklistController(WorklistService worklistService) {
        this.worklistService = worklistService;
    }

    /**
     * Get the pharmacy worklist for a facility, filtered by optional parameters.
     */
    @GetMapping("/worklists")
    public ResponseEntity<ApiResponse<PagedResponse<DispenseOrderSummaryDto>>> getWorklist(
            @RequestParam UUID facilityId,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) DispenseStatus status,
            @RequestParam(required = false) OrderPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<DispenseOrderEntity> result = worklistService.getWorklist(
                facilityId, workspaceId, storeId, status, priority, PageRequest.of(page, size));

        List<DispenseOrderSummaryDto> items = result.getContent().stream()
                .map(DispenseOrderSummaryDto::from)
                .collect(Collectors.toList());

        PagedResponse<DispenseOrderSummaryDto> pagedResponse = PagedResponse.of(
                items, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, correlationId));
    }
}
