package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.ClinicalTimelineEntry;
import zw.gov.mohcc.impilo.experience.repository.ClinicalTimelineRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.util.*;

/**
 * Clinical timeline endpoints.
 * GET /internal/v1/timeline?patient_id= — list timeline for patient (paged, desc by occurred_at).
 * GET /internal/v1/timeline?encounter_id= — list timeline for encounter (list, no pagination).
 */
@RestController
@RequestMapping("/internal/v1/timeline")
public class ClinicalTimelineController {

    private final ClinicalTimelineRepository clinicalTimelineRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public ClinicalTimelineController(ClinicalTimelineRepository clinicalTimelineRepository,
                                      OutboxService outboxService,
                                      JdbcTemplate jdbcTemplate) {
        this.clinicalTimelineRepository = clinicalTimelineRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTimeline(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "encounter_id") String encounterId) {

        if (encounterId != null) {
            List<ClinicalTimelineEntry> entries = clinicalTimelineRepository
                    .findByTenantIdAndEncounterIdOrderByOccurredAtDesc(tenantId, UUID.fromString(encounterId));

            List<Map<String, Object>> data = entries.stream()
                    .map(this::toResource)
                    .toList();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            response.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId
            ));

            return ResponseEntity.ok(response);
        }

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("occurredAt").descending());

        Page<ClinicalTimelineEntry> result;
        if (patientId != null) {
            result = clinicalTimelineRepository.findByTenantIdAndPatientIdOrderByOccurredAtDesc(
                    tenantId, UUID.fromString(patientId), pageable);
        } else {
            result = clinicalTimelineRepository.findAll(pageable);
        }

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(ClinicalTimelineEntry e) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", e.getPatientId());
        attributes.put("encounter_id", e.getEncounterId());
        attributes.put("event_type", e.getEventType());
        attributes.put("title", e.getTitle());
        attributes.put("description", e.getDescription());
        attributes.put("source_type", e.getSourceType());
        attributes.put("source_id", e.getSourceId());
        attributes.put("actor_id", e.getActorId());
        attributes.put("actor_name", e.getActorName());
        attributes.put("occurred_at", e.getOccurredAt());
        attributes.put("created_at", e.getCreatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", e.getId().toString());
        resource.put("type", "ClinicalTimelineEntry");
        resource.put("attributes", attributes);
        return resource;
    }
}
