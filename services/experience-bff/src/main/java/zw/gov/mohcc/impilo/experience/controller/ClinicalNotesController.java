package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.domain.ClinicalNote;
import zw.gov.mohcc.impilo.experience.repository.ClinicalNoteRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

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

    private final ClinicalNoteRepository clinicalNoteRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate; // TODO: remove after verification
    private final PctServiceClient pctClient;

    public ClinicalNotesController(ClinicalNoteRepository clinicalNoteRepository,
                                   OutboxService outboxService,
                                   JdbcTemplate jdbcTemplate,
                                   PctServiceClient pctClient) {
        this.clinicalNoteRepository = clinicalNoteRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
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

        // STRANGLER: delegate to PctServiceClient
        if (patientId != null) {
            try {
                JsonNode pctData = pctClient.listClinicalNotes(patientId, page, Math.min(size, 100));
                if (pctData != null) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("data", pctData);
                    response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                log.warn("PCT listClinicalNotes failed, falling back to local: {}", e.getMessage());
            }
        }

        // STRANGLER: migrated to PctServiceClient — fallback to local repository
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<ClinicalNote> result;
        if (patientId != null) {
            result = clinicalNoteRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);
        } else {
            result = clinicalNoteRepository.findAll(pageable);
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getNote(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        ClinicalNote note = clinicalNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical note not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(note));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createNote(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateNoteRequest request) {

        UUID noteId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // STRANGLER: delegate to PctServiceClient first
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

        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache
        jdbcTemplate.update("""
            INSERT INTO clinical_notes
                (id, tenant_id, patient_id, encounter_id, note_type,
                 subjective, objective, assessment, plan, body,
                 author_id, author_name, status, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
            """,
                noteId, tenantId, request.patient_id(), request.encounter_id(),
                request.note_type(),
                request.subjective(), request.objective(), request.assessment(),
                request.plan(), request.body(),
                request.author_id(), request.author_name(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.note.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "ClinicalNote",
                noteId.toString(),
                Map.of(
                        "note_id", noteId.toString(),
                        "patient_id", request.patient_id(),
                        "note_type", request.note_type() != null ? request.note_type() : "",
                        "author_id", request.author_id(),
                        "status", "DRAFT"
                ),
                Map.of()
        );

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
    @Transactional
    public ResponseEntity<Map<String, Object>> signNote(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        ClinicalNote note = clinicalNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical note not found: " + id));

        // STRANGLER: delegate to PctServiceClient first
        try {
            pctClient.signClinicalNote(id.toString());
            log.info("PCT clinical note signed: {}", id);
        } catch (Exception e) {
            log.warn("PCT signClinicalNote failed (non-blocking): {}", e.getMessage());
        }

        note.sign();
        clinicalNoteRepository.save(note);

        outboxService.writeOutboxEvent(
                "impilo.experience.note.signed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "ClinicalNote",
                id.toString(),
                Map.of(
                        "note_id", id.toString(),
                        "patient_id", note.getPatientId().toString(),
                        "status", "SIGNED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(note));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(ClinicalNote n) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", n.getPatientId());
        attributes.put("encounter_id", n.getEncounterId());
        attributes.put("note_type", n.getNoteType());
        attributes.put("subjective", n.getSubjective());
        attributes.put("objective", n.getObjective());
        attributes.put("assessment", n.getAssessment());
        attributes.put("plan", n.getPlan());
        attributes.put("body", n.getBody());
        attributes.put("author_id", n.getAuthorId());
        attributes.put("author_name", n.getAuthorName());
        attributes.put("status", n.getStatus());
        attributes.put("signed_at", n.getSignedAt());
        attributes.put("created_at", n.getCreatedAt());
        attributes.put("updated_at", n.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", n.getId().toString());
        resource.put("type", "ClinicalNote");
        resource.put("attributes", attributes);
        return resource;
    }
}
