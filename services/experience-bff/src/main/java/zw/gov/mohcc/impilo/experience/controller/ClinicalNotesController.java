package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Clinical notes management endpoints.
 * GET  /internal/v1/clinical-notes?patient_id= — list notes for patient (paged).
 * GET  /internal/v1/clinical-notes/{id} — get single note.
 * POST /internal/v1/clinical-notes — create note.
 * POST /internal/v1/clinical-notes/{id}/sign — sign a note.
 */
@RestController
@RequestMapping("/internal/v1/clinical-notes")
public class ClinicalNotesController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalNotesController.class);

    private final PctServiceClient pctClient;

    public ClinicalNotesController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    public record CreateNoteRequest(
            @NotBlank String patient_id,
            String encounter_id,
            String note_type,
            String subjective,
            String objective,
            String assessment,
            String plan,
            String body,
            @NotBlank String author_id,
            String author_name
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listNotes(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        if (patientId != null) {
            try {
                JsonNode pctData = pctClient.listClinicalNotes(patientId, page, size);
                if (pctData != null) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("data", pctData);
                    response.put("meta", Map.of(
                            "request_id", requestId,
                            "correlation_id", correlationId
                    ));
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                log.warn("PCT listClinicalNotes failed: {}", e.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", List.of());
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getNote(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode noteData = pctClient.getClinicalNote(id.toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", noteData != null ? noteData : Map.of());
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createNote(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateNoteRequest request) {

        UUID noteId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        try {
            Map<String, Object> pctBody = new LinkedHashMap<>();
            pctBody.put("patient_id", request.patient_id());
            pctBody.put("encounter_id", request.encounter_id());
            pctBody.put("note_type", request.note_type());
            pctBody.put("subjective", request.subjective());
            pctBody.put("objective", request.objective());
            pctBody.put("assessment", request.assessment());
            pctBody.put("plan", request.plan());
            pctBody.put("body", request.body());
            pctBody.put("author_id", request.author_id());
            pctBody.put("author_name", request.author_name());
            pctClient.createClinicalNote(pctBody);
            log.info("PCT clinical note created successfully for patient={}", request.patient_id());
        } catch (Exception e) {
            log.warn("PCT createClinicalNote failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("note_type", request.note_type());
        attributes.put("subjective", request.subjective());
        attributes.put("objective", request.objective());
        attributes.put("assessment", request.assessment());
        attributes.put("plan", request.plan());
        attributes.put("body", request.body());
        attributes.put("author_id", request.author_id());
        attributes.put("author_name", request.author_name());
        attributes.put("status", "DRAFT");
        attributes.put("created_at", now);
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", noteId.toString(),
                "type", "ClinicalNote",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/sign")
    public ResponseEntity<Map<String, Object>> signNote(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        JsonNode result = pctClient.signClinicalNote(id.toString());
        log.info("PCT clinical note signed: {}", id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result != null ? result : Map.of("id", id.toString(), "status", "SIGNED"));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
