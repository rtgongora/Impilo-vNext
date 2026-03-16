package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.VitalsRecord;
import zw.gov.mohcc.impilo.experience.repository.VitalsRecordRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Vitals management endpoints.
 * GET  /internal/v1/vitals?patient_id= — list vitals for patient (paged).
 * GET  /internal/v1/vitals?encounter_id= — list vitals for encounter.
 * POST /internal/v1/vitals — record new vitals entry.
 */
@RestController
@RequestMapping("/internal/v1/vitals")
public class VitalsController {

    private final VitalsRecordRepository vitalsRecordRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public VitalsController(VitalsRecordRepository vitalsRecordRepository,
                            OutboxService outboxService,
                            JdbcTemplate jdbcTemplate) {
        this.vitalsRecordRepository = vitalsRecordRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateVitalsRequest(
            @NotBlank String patient_id,
            String encounter_id,
            String recorded_by,
            Integer systolic,
            Integer diastolic,
            Integer heart_rate,
            BigDecimal temperature,
            Integer respiratory_rate,
            BigDecimal oxygen_saturation,
            BigDecimal weight,
            BigDecimal height,
            Integer pain_score,
            String notes
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "encounter_id") String encounterId) {

        if (encounterId != null) {
            List<VitalsRecord> records = vitalsRecordRepository.findByEncounterId(UUID.fromString(encounterId));

            List<Map<String, Object>> data = records.stream()
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

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("recordedAt").descending());

        Page<VitalsRecord> result;
        if (patientId != null) {
            result = vitalsRecordRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);
        } else {
            result = vitalsRecordRepository.findAll(pageable);
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

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateVitalsRequest request) {

        UUID vitalsId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Calculate BMI if weight and height are provided
        BigDecimal bmi = null;
        if (request.weight() != null && request.height() != null
                && request.height().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightM = request.height().divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
            bmi = request.weight().divide(heightM.multiply(heightM), 2, java.math.RoundingMode.HALF_UP);
        }

        jdbcTemplate.update("""
            INSERT INTO vitals_records
                (id, tenant_id, patient_id, encounter_id, recorded_by,
                 systolic, diastolic, heart_rate, temperature, respiratory_rate,
                 oxygen_saturation, weight, height, bmi, pain_score, notes,
                 recorded_at, created_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                vitalsId, tenantId, request.patient_id(),
                request.encounter_id(),
                request.recorded_by(),
                request.systolic(), request.diastolic(), request.heart_rate(),
                request.temperature(), request.respiratory_rate(),
                request.oxygen_saturation(), request.weight(), request.height(),
                bmi, request.pain_score(), request.notes(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.vitals.recorded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "VitalsRecord",
                vitalsId.toString(),
                Map.of(
                        "vitals_id", vitalsId.toString(),
                        "patient_id", request.patient_id(),
                        "encounter_id", request.encounter_id() != null ? request.encounter_id() : "",
                        "recorded_by", request.recorded_by() != null ? request.recorded_by() : ""
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("recorded_by", request.recorded_by());
        attributes.put("systolic", request.systolic());
        attributes.put("diastolic", request.diastolic());
        attributes.put("heart_rate", request.heart_rate());
        attributes.put("temperature", request.temperature());
        attributes.put("respiratory_rate", request.respiratory_rate());
        attributes.put("oxygen_saturation", request.oxygen_saturation());
        attributes.put("weight", request.weight());
        attributes.put("height", request.height());
        attributes.put("bmi", bmi);
        attributes.put("pain_score", request.pain_score());
        attributes.put("notes", request.notes());
        attributes.put("recorded_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", vitalsId.toString(),
                "type", "VitalsRecord",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Map<String, Object> toResource(VitalsRecord v) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", v.getPatientId());
        attributes.put("encounter_id", v.getEncounterId());
        attributes.put("recorded_by", v.getRecordedBy());
        attributes.put("systolic", v.getSystolic());
        attributes.put("diastolic", v.getDiastolic());
        attributes.put("heart_rate", v.getHeartRate());
        attributes.put("temperature", v.getTemperature());
        attributes.put("respiratory_rate", v.getRespiratoryRate());
        attributes.put("oxygen_saturation", v.getOxygenSaturation());
        attributes.put("weight", v.getWeight());
        attributes.put("height", v.getHeight());
        attributes.put("bmi", v.getBmi());
        attributes.put("pain_score", v.getPainScore());
        attributes.put("notes", v.getNotes());
        attributes.put("recorded_at", v.getRecordedAt());
        attributes.put("created_at", v.getCreatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", v.getId().toString());
        resource.put("type", "VitalsRecord");
        resource.put("attributes", attributes);
        return resource;
    }
}
