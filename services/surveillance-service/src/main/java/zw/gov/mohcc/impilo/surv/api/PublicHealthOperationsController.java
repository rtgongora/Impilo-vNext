package zw.gov.mohcc.impilo.surv.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.surv.core.*;
import zw.gov.mohcc.impilo.surv.persistence.entity.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedicated public-health operations API surface.
 */
@RestController
@RequestMapping("/internal/v1/public-health")
public class PublicHealthOperationsController {

    private final CounterService counterService;
    private final IngestService ingestService;
    private final ObjectMapper objectMapper;
    private final OutbreakEventService outbreakEventService;
    private final FieldTaskService fieldTaskService;
    private final InvestigationService investigationService;
    private final IntelligenceBriefService intelligenceBriefService;
    private final PublicHealthOperationsHomeService operationsHomeService;
    private final PublicHealthContextService contextService;
    private final EnvironmentalComplaintService environmentalComplaintService;

    public PublicHealthOperationsController(
            CounterService counterService,
            IngestService ingestService,
            ObjectMapper objectMapper,
            OutbreakEventService outbreakEventService,
            FieldTaskService fieldTaskService,
            InvestigationService investigationService,
            IntelligenceBriefService intelligenceBriefService,
            PublicHealthOperationsHomeService operationsHomeService,
            PublicHealthContextService contextService,
            EnvironmentalComplaintService environmentalComplaintService) {
        this.counterService = counterService;
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
        this.outbreakEventService = outbreakEventService;
        this.fieldTaskService = fieldTaskService;
        this.investigationService = investigationService;
        this.intelligenceBriefService = intelligenceBriefService;
        this.operationsHomeService = operationsHomeService;
        this.contextService = contextService;
        this.environmentalComplaintService = environmentalComplaintService;
    }

    @GetMapping("/context")
    public ResponseEntity<?> workspaceContext(
            @RequestParam(required = false) String jurisdiction_pack,
            @RequestParam(required = false) String workspace_id,
            @RequestParam(required = false) String facility_id) {
        return ResponseEntity.ok(contextService.buildContext(workspace_id, facility_id, jurisdiction_pack));
    }

    @GetMapping("/operations-home")
    public ResponseEntity<?> operationsHome() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        return ResponseEntity.ok(operationsHomeService.buildHome(tenantId));
    }

    @GetMapping("/map-markers")
    public ResponseEntity<?> mapMarkers() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("markers", operationsHomeService.buildMapMarkers(tenantId));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/nompilo/situation-summary")
    public ResponseEntity<?> nompiloSituationSummary() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        return ResponseEntity.ok(operationsHomeService.buildNompiloSummary(tenantId));
    }

    @GetMapping("/weekly-idsr")
    public ResponseEntity<?> weeklyIdsr(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        List<zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity> counters =
                counterService.getCounters(tenantId, from, to);
        Map<String, Long> totalsBySyndrome = counters.stream()
                .collect(Collectors.groupingBy(
                        zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity::getSyndromeCode,
                        Collectors.summingLong(
                                zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity::getEventCount)));

        List<Map<String, Object>> rows = totalsBySyndrome.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of(
                        "syndrome_code", entry.getKey(),
                        "event_count", entry.getValue(),
                        "period_start", from != null ? from.toString() : "",
                        "period_end", to != null ? to.toString() : ""))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report_type", "WEEKLY_IDSR_AGGREGATE");
        body.put("derivation", "SURVEILLANCE_COUNTERS");
        body.put("rows", rows);
        body.put("total_events", rows.stream().mapToLong(r -> ((Number) r.get("event_count")).longValue()).sum());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/outbreaks")
    public ResponseEntity<?> listOutbreaks() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<OutbreakEventEntity> items = outbreakEventService.listOutbreaks(tenantId);
        return ResponseEntity.ok(Map.of("items", items, "total_elements", items.size()));
    }

    @PostMapping("/outbreaks")
    public ResponseEntity<?> recordOutbreak(@RequestBody CreateOutbreakRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        UUID correlationId = UUID.fromString(ctx.correlationId());

        if (request.name() == null || request.name().isBlank()) {
            return badRequest(ctx, "name is required");
        }

        try {
            OutbreakEventEntity outbreak = outbreakEventService.recordOutbreak(
                    tenantId,
                    ctx.podId(),
                    correlationId,
                    request.name(),
                    request.description(),
                    request.eventType(),
                    request.conditionField(),
                    request.threshold(),
                    request.windowHours(),
                    OutbreakEventService.parseSeverity(request.severity()),
                    request.province(),
                    request.district(),
                    request.facilityId(),
                    request.latitude(),
                    request.longitude());

            Map<String, Object> response = outbreakResponse(outbreak);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return badRequest(ctx, ex.getMessage());
        }
    }

    @PostMapping("/outbreaks/{outbreakId}/transition")
    public ResponseEntity<?> transitionOutbreak(
            @PathVariable Long outbreakId,
            @RequestBody TransitionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        if (request.lifecycleStatus() == null || request.lifecycleStatus().isBlank()) {
            return badRequest(ctx, "lifecycle_status is required");
        }
        try {
            OutbreakLifecycleStatus next = OutbreakLifecycleStatus.valueOf(
                    request.lifecycleStatus().trim().toUpperCase(Locale.ROOT));
            OutbreakEventEntity outbreak = outbreakEventService.transition(tenantId, outbreakId, next);
            return ResponseEntity.ok(outbreakResponse(outbreak));
        } catch (IllegalArgumentException ex) {
            return badRequest(ctx, ex.getMessage());
        }
    }

    @GetMapping("/field-tasks")
    public ResponseEntity<?> listFieldTasks() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<FieldTaskEntity> items = fieldTaskService.listTasks(tenantId);
        return ResponseEntity.ok(Map.of("items", items, "total_elements", items.size()));
    }

    @PostMapping("/field-tasks")
    public ResponseEntity<?> createFieldTask(@RequestBody CreateFieldTaskRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        if (request.facilityId() == null) {
            return badRequest(ctx, "facility_id is required");
        }

        FieldTaskEntity task = fieldTaskService.createTask(
                tenantId,
                ctx.podId(),
                request.title(),
                request.taskType(),
                request.facilityId(),
                request.outbreakId(),
                request.caseId(),
                request.assignedTo(),
                request.latitude(),
                request.longitude(),
                parseDate(request.dueDate()),
                request.details());

        return ResponseEntity.status(HttpStatus.CREATED).body(fieldTaskResponse(task));
    }

    @PostMapping("/field-tasks/{taskId}/transition")
    public ResponseEntity<?> transitionFieldTask(
            @PathVariable Long taskId,
            @RequestBody TransitionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        if (request.status() == null || request.status().isBlank()) {
            return badRequest(ctx, "status is required");
        }
        try {
            FieldTaskStatus next = FieldTaskStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
            FieldTaskEntity task = fieldTaskService.transition(tenantId, taskId, next, request.assignedTo());
            return ResponseEntity.ok(fieldTaskResponse(task));
        } catch (IllegalArgumentException ex) {
            return badRequest(ctx, ex.getMessage());
        }
    }

    @PostMapping("/field-operations")
    public ResponseEntity<?> recordFieldOperation(@RequestBody CreateFieldOperationRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        UUID correlationId = UUID.fromString(ctx.correlationId());

        if (request.facilityId() == null) {
            return badRequest(ctx, "facility_id is required");
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "operation_type", textOrDefault(request.operationType(), "FIELD_OPERATION"),
                    "status", textOrDefault(request.status(), "PLANNED"),
                    "occurred_on", textOrDefault(request.occurredOn(), LocalDate.now().toString()),
                    "details", request.details() != null ? request.details() : Map.of()));
        } catch (JsonProcessingException e) {
            return badRequest(ctx, "details payload must be serializable");
        }

        IngestService.IngestResult result = ingestService.ingest(
                tenantId,
                ctx.podId(),
                correlationId,
                "FIELD_OPERATION",
                payload,
                request.facilityId(),
                ctx.correlationId());

        String title = request.details() != null && request.details().get("activityTitle") != null
                ? String.valueOf(request.details().get("activityTitle"))
                : textOrDefault(request.operationType(), "Field operation");

        FieldTaskEntity task = fieldTaskService.createTask(
                tenantId,
                ctx.podId(),
                title,
                textOrDefault(request.operationType(), "FIELD_OPERATION"),
                request.facilityId(),
                null,
                null,
                request.details() != null ? String.valueOf(request.details().getOrDefault("teamLeaderName", "")) : null,
                parseDouble(request.details(), "gpsLat", "latitude"),
                parseDouble(request.details(), "gpsLng", "longitude"),
                parseDate(request.occurredOn()),
                request.details());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operation_id", task.getId());
        response.put("task_id", task.getId());
        response.put("lifecycle_status", task.getStatus().name());
        response.put("signals_matched", result.signalsMatched());
        response.put("hits_recorded", result.hitsRecorded());
        response.put("cases_opened", result.casesOpened());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/investigations")
    public ResponseEntity<?> listInvestigations() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<InvestigationEntity> items = investigationService.listInvestigations(tenantId);
        return ResponseEntity.ok(Map.of("items", items, "total_elements", items.size()));
    }

    @PostMapping("/investigations")
    public ResponseEntity<?> openInvestigation(@RequestBody CreateInvestigationRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        InvestigationEntity investigation = investigationService.openInvestigation(
                tenantId,
                ctx.podId(),
                request.title(),
                request.caseId(),
                request.outbreakId(),
                request.facilityId(),
                request.assignedTo());

        return ResponseEntity.status(HttpStatus.CREATED).body(investigationResponse(investigation));
    }

    @PostMapping("/investigations/{investigationId}/status")
    public ResponseEntity<?> updateInvestigationStatus(
            @PathVariable Long investigationId,
            @RequestBody InvestigationStatusRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        if (request.status() == null || request.status().isBlank()) {
            return badRequest(ctx, "status is required");
        }
        try {
            InvestigationStatus status = InvestigationStatus.valueOf(
                    request.status().trim().toUpperCase(Locale.ROOT));
            InvestigationEntity investigation = investigationService.updateStatus(
                    tenantId, investigationId, status, request.findings());
            return ResponseEntity.ok(investigationResponse(investigation));
        } catch (IllegalArgumentException ex) {
            return badRequest(ctx, ex.getMessage());
        }
    }

    @GetMapping("/reports/briefs")
    public ResponseEntity<?> listBriefs() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<IntelligenceBriefEntity> items = intelligenceBriefService.listBriefs(tenantId);
        return ResponseEntity.ok(Map.of("items", items, "total_elements", items.size()));
    }

    @PostMapping("/reports/briefs/generate")
    public ResponseEntity<?> generateBrief(@RequestBody GenerateBriefRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        IntelligenceBriefEntity brief = intelligenceBriefService.generateDraft(
                tenantId, ctx.podId(), request.title(), request.briefType());
        return ResponseEntity.status(HttpStatus.CREATED).body(briefResponse(brief));
    }

    @GetMapping("/complaints")
    public ResponseEntity<?> listComplaints() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<EnvironmentalComplaintEntity> items = environmentalComplaintService.listComplaints(tenantId);
        return ResponseEntity.ok(Map.of("items", items, "total_elements", items.size()));
    }

    @PostMapping("/complaints")
    public ResponseEntity<?> fileComplaint(@RequestBody FileComplaintRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        if (request.title() == null || request.title().isBlank()) {
            return badRequest(ctx, "title is required");
        }

        EnvironmentalComplaintEntity complaint = environmentalComplaintService.fileComplaint(
                tenantId,
                ctx.podId(),
                request.category(),
                request.title(),
                request.description(),
                request.province(),
                request.district(),
                request.siteId(),
                request.facilityId(),
                request.reporterContact(),
                request.jurisdictionPack());

        return ResponseEntity.status(HttpStatus.CREATED).body(complaintResponse(complaint));
    }

    @PostMapping("/complaints/{complaintId}/transition")
    public ResponseEntity<?> transitionComplaint(
            @PathVariable Long complaintId,
            @RequestBody TransitionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        if (request.status() == null || request.status().isBlank()) {
            return badRequest(ctx, "status is required");
        }
        try {
            ComplaintStatus next = ComplaintStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
            EnvironmentalComplaintEntity complaint =
                    environmentalComplaintService.transition(tenantId, complaintId, next);
            return ResponseEntity.ok(complaintResponse(complaint));
        } catch (IllegalArgumentException ex) {
            return badRequest(ctx, ex.getMessage());
        }
    }

    @PostMapping("/reports/briefs/{briefId}/publish")
    public ResponseEntity<?> publishBrief(@PathVariable Long briefId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        try {
            IntelligenceBriefEntity brief = intelligenceBriefService.publish(tenantId, briefId);
            return ResponseEntity.ok(briefResponse(brief));
        } catch (IllegalArgumentException ex) {
            return badRequest(ctx, ex.getMessage());
        }
    }

    @PostMapping("/reports/briefs/{briefId}/exports")
    public ResponseEntity<?> exportBrief(
            @PathVariable Long briefId,
            @RequestBody BriefExportRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        try {
            return ResponseEntity.ok(intelligenceBriefService.export(tenantId, briefId, request.format()));
        } catch (IllegalArgumentException ex) {
            return badRequest(ctx, ex.getMessage());
        }
    }

    private Map<String, Object> outbreakResponse(OutbreakEventEntity outbreak) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("outbreak_id", outbreak.getId());
        response.put("lifecycle_status", outbreak.getLifecycleStatus().name());
        response.put("signal_reference", outbreak.getSignalId());
        response.put("name", outbreak.getName());
        response.put("severity", outbreak.getSeverity());
        response.put("event_type", outbreak.getEventType());
        response.put("province", outbreak.getProvince());
        response.put("district", outbreak.getDistrict());
        response.put("latitude", outbreak.getLatitude());
        response.put("longitude", outbreak.getLongitude());
        return response;
    }

    private Map<String, Object> fieldTaskResponse(FieldTaskEntity task) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task_id", task.getId());
        response.put("status", task.getStatus().name());
        response.put("title", task.getTitle());
        response.put("task_type", task.getTaskType());
        response.put("facility_id", task.getFacilityId());
        response.put("assigned_to", task.getAssignedTo());
        return response;
    }

    private Map<String, Object> investigationResponse(InvestigationEntity investigation) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("investigation_id", investigation.getId());
        response.put("status", investigation.getStatus().name());
        response.put("title", investigation.getTitle());
        response.put("case_id", investigation.getCaseId());
        response.put("outbreak_id", investigation.getOutbreakId());
        return response;
    }

    private Map<String, Object> briefResponse(IntelligenceBriefEntity brief) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("brief_id", brief.getId());
        response.put("title", brief.getTitle());
        response.put("brief_type", brief.getBriefType());
        response.put("status", brief.getStatus().name());
        response.put("summary_markdown", brief.getSummaryMarkdown());
        response.put("published_at", brief.getPublishedAt());
        return response;
    }

    private ResponseEntity<?> badRequest(RequestContext ctx, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of("INVALID_REQUEST", message, ctx.requestId(), ctx.correlationId()));
    }

    private String textOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double parseDouble(Map<String, Object> details, String... keys) {
        if (details == null) {
            return null;
        }
        for (String key : keys) {
            Object val = details.get(key);
            if (val == null) {
                continue;
            }
            try {
                return Double.parseDouble(val.toString());
            } catch (NumberFormatException ignored) {
                // try next key
            }
        }
        return null;
    }

    public record CreateOutbreakRequest(
            @NotBlank String name,
            String description,
            String eventType,
            String conditionField,
            int threshold,
            int windowHours,
            String severity,
            String province,
            String district,
            UUID facilityId,
            Double latitude,
            Double longitude) {
    }

    public record CreateFieldOperationRequest(
            UUID facilityId,
            String operationType,
            String status,
            String occurredOn,
            Map<String, Object> details) {
    }

    public record CreateFieldTaskRequest(
            UUID facilityId,
            String title,
            String taskType,
            Long outbreakId,
            Long caseId,
            String assignedTo,
            Double latitude,
            Double longitude,
            String dueDate,
            Map<String, Object> details) {
    }

    public record CreateInvestigationRequest(
            String title,
            Long caseId,
            Long outbreakId,
            UUID facilityId,
            String assignedTo) {
    }

    public record TransitionRequest(
            String lifecycleStatus,
            String status,
            String assignedTo) {
    }

    public record InvestigationStatusRequest(String status, String findings) {
    }

    public record GenerateBriefRequest(String title, String briefType) {
    }

    public record BriefExportRequest(String format) {
    }

    public record FileComplaintRequest(
            String category,
            String title,
            String description,
            String province,
            String district,
            UUID siteId,
            UUID facilityId,
            String reporterContact,
            String jurisdictionPack) {
    }

    private Map<String, Object> complaintResponse(EnvironmentalComplaintEntity complaint) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("complaint_id", complaint.getId());
        response.put("title", complaint.getTitle());
        response.put("category", complaint.getCategory());
        response.put("status", complaint.getStatus().name());
        response.put("province", complaint.getProvince());
        response.put("district", complaint.getDistrict());
        response.put("jurisdiction_pack", complaint.getJurisdictionPack());
        return response;
    }
}
