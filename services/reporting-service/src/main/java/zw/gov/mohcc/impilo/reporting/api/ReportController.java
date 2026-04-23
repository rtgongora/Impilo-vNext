package zw.gov.mohcc.impilo.reporting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.reporting.core.ReportDefinitionService;
import zw.gov.mohcc.impilo.reporting.core.ReportRunService;
import zw.gov.mohcc.impilo.reporting.core.ScheduleService;
import zw.gov.mohcc.impilo.reporting.dto.*;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportRunEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportScheduleEntity;
import zw.gov.mohcc.impilo.shared.visibility.ExportVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/reports")
public class ReportController {

    private final ReportDefinitionService definitionService;
    private final ReportRunService runService;
    private final ScheduleService scheduleService;
    private final ObjectMapper objectMapper;

    public ReportController(ReportDefinitionService definitionService,
                            ReportRunService runService,
                            ScheduleService scheduleService,
                            ObjectMapper objectMapper) {
        this.definitionService = definitionService;
        this.runService = runService;
        this.scheduleService = scheduleService;
        this.objectMapper = objectMapper;
    }

    /** Create a new report definition. */
    @PostMapping
    public ResponseEntity<?> createReport(@Valid @RequestBody CreateReportRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        try {
            ReportDefinitionEntity entity = definitionService.createReport(
                    tenantId, ctx.correlationId(), request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(definitionService.toResponse(entity));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new ErrorEnvelope(new ErrorEnvelope.ErrorBody(
                            "REPORT_KEY_EXISTS",
                            e.getMessage(),
                            Map.of("report_key", request.reportKey()),
                            ctx.requestId(),
                            ctx.correlationId()
                    ))
            );
        }
    }

    /** Run a report by its key. */
    @PostMapping("/{key}/run")
    public ResponseEntity<?> runReport(@PathVariable String key,
                                       @RequestBody(required = false) RunReportRequest request,
                                       HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        VisibilityProfile visibility = VisibilityHeaderParser.resolve(httpRequest, objectMapper);
        if (ExportVisibilityGuard.deniesReportRun(visibility)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    new ErrorEnvelope(new ErrorEnvelope.ErrorBody(
                            "EXPORT_VISIBILITY_DENIED",
                            "Report execution is not permitted for the current data visibility and export policy.",
                            java.util.Map.of("report_key", key),
                            ctx.requestId(),
                            ctx.correlationId()
                    ))
            );
        }

        try {
            ReportRunEntity run = runService.runReport(
                    tenantId, key, ctx.correlationId(), request, visibility);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(runService.toResponse(run));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorEnvelope(new ErrorEnvelope.ErrorBody(
                            "REPORT_NOT_FOUND",
                            e.getMessage(),
                            Map.of("report_key", key),
                            ctx.requestId(),
                            ctx.correlationId()
                    ))
            );
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new ErrorEnvelope(new ErrorEnvelope.ErrorBody(
                            "REPORT_NOT_ACTIVE",
                            e.getMessage(),
                            Map.of("report_key", key),
                            ctx.requestId(),
                            ctx.correlationId()
                    ))
            );
        }
    }

    /** List runs for a report by its key. */
    @GetMapping("/{key}/runs")
    public ResponseEntity<?> listRuns(@PathVariable String key,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<ReportRunEntity> runs = runService.listRuns(tenantId, key, pageable);
            List<ReportRunResponse> items = runs.getContent().stream()
                    .map(runService::toResponse)
                    .toList();

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("items", items);
            response.put("page", runs.getNumber());
            response.put("size", runs.getSize());
            response.put("totalElements", runs.getTotalElements());
            response.put("totalPages", runs.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorEnvelope(new ErrorEnvelope.ErrorBody(
                            "REPORT_NOT_FOUND",
                            e.getMessage(),
                            Map.of("report_key", key),
                            ctx.requestId(),
                            ctx.correlationId()
                    ))
            );
        }
    }

    /** Create a schedule for a report. */
    @PostMapping("/{key}/schedules")
    public ResponseEntity<?> createSchedule(@PathVariable String key,
                                             @Valid @RequestBody CreateScheduleRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        try {
            ReportScheduleEntity schedule = scheduleService.createSchedule(
                    tenantId, key, ctx.correlationId(), request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(scheduleService.toResponse(schedule));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorEnvelope(new ErrorEnvelope.ErrorBody(
                            "REPORT_NOT_FOUND",
                            e.getMessage(),
                            Map.of("report_key", key),
                            ctx.requestId(),
                            ctx.correlationId()
                    ))
            );
        }
    }
}
