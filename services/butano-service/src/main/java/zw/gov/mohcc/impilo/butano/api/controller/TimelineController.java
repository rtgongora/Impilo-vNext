package zw.gov.mohcc.impilo.butano.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.butano.core.TimelineItem;
import zw.gov.mohcc.impilo.butano.core.TimelineService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;

/**
 * REST controller for patient clinical timelines.
 *
 * Returns a paginated, chronologically-sorted timeline of clinical events
 * for a given CPID. Supports filtering by resource type and date range.
 */
@RestController
@RequestMapping("/v1/timeline")
public class TimelineController {

    private static final Logger log = LoggerFactory.getLogger(TimelineController.class);

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    /**
     * Get a paginated timeline of clinical events for a patient.
     *
     * @param cpid  the Clinical Patient Identifier
     * @param since ISO date string; only include events on or after this date
     * @param type  comma-separated resource type names (e.g., "Encounter,Condition,Observation")
     * @param page  zero-based page index (default 0)
     * @param size  page size (default 20)
     * @return paginated list of timeline items sorted newest-first
     */
    @GetMapping("/{cpid}")
    public ResponseEntity<ApiResponse<PagedResponse<TimelineItem>>> getTimeline(
            @PathVariable String cpid,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Timeline requested for CPID={} by actor={} since={} types={}",
                cpid, ctx.actorId(), since, type);

        // Clamp page size to reasonable bounds
        int clampedSize = Math.max(1, Math.min(size, 200));

        List<TimelineItem> items = timelineService.buildTimeline(cpid, since, type, page, clampedSize);
        long totalElements = timelineService.countTimelineItems(cpid, since, type);

        PagedResponse<TimelineItem> pagedResponse = PagedResponse.of(
                items, page, clampedSize, totalElements);

        return ResponseEntity.ok(
                ApiResponse.ok(pagedResponse, ctx.correlationId().toString()));
    }
}
