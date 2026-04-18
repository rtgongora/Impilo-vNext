package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.FormsServiceClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile dynamic form endpoints.
 * GET  /internal/v1/mobile/provider/forms                           - list form schemas
 * GET  /internal/v1/mobile/provider/forms/{id}                      - get form schema
 * POST /internal/v1/mobile/provider/forms/{id}/submit               - submit form
 * POST /internal/v1/mobile/provider/forms/submissions               - submit form (legacy)
 * GET  /internal/v1/mobile/provider/forms/submissions?encounter_id= - list submissions
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/forms")
public class MobileFormController {

    private final ObjectMapper objectMapper;
    private final FormsServiceClient formsClient;

    public MobileFormController(ObjectMapper objectMapper, FormsServiceClient formsClient) {
        this.objectMapper = objectMapper;
        this.formsClient = formsClient;
    }

    public record SubmitFormRequest(
            @NotBlank String form_id,
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String submitted_by,
            @NotNull Map<String, Object> form_data
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listForms(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        JsonNode schemas = formsClient.listSchemas();
        if (schemas != null) {
            return ResponseEntity.ok(Map.of(
                    "data", schemas,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getForm(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode schema = formsClient.getSchema(id.toString());
        if (schema != null) {
            return ResponseEntity.ok(Map.of(
                    "data", schema,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
        return ResponseEntity.ok(Map.of(
                "data", Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    public record SubmitFormByIdRequest(
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String submitted_by,
            @NotNull Map<String, Object> form_data
    ) {}

    @PostMapping("/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitFormById(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SubmitFormByIdRequest request) {

        UUID submissionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        String formDataJson;
        try {
            formDataJson = objectMapper.writeValueAsString(request.form_data());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize form data", e);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("form_id", id.toString());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("submitted_by", request.submitted_by());
        attributes.put("form_data", request.form_data());
        attributes.put("status", "SUBMITTED");
        attributes.put("submitted_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", submissionId.toString(),
                "type", "FormSubmission",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/submissions")
    public ResponseEntity<Map<String, Object>> submitForm(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SubmitFormRequest request) {

        UUID submissionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        String formDataJson;
        try {
            formDataJson = objectMapper.writeValueAsString(request.form_data());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize form data", e);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("form_id", request.form_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("submitted_by", request.submitted_by());
        attributes.put("form_data", request.form_data());
        attributes.put("status", "SUBMITTED");
        attributes.put("submitted_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", submissionId.toString(),
                "type", "FormSubmission",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/submissions")
    public ResponseEntity<Map<String, Object>> listSubmissions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "encounter_id") String encounterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode submissions = formsClient.listSubmissions(encounterId, page, size);
            if (submissions != null) {
                return ResponseEntity.ok(Map.of(
                        "data", submissions,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception e) {
            // Forms-service submissions may not be available
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    private Map<String, Object> toFormResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", row.get("name"));
        attributes.put("description", row.get("description"));
        attributes.put("category", row.get("category"));
        attributes.put("version", row.get("version"));
        attributes.put("status", row.get("status"));
        attributes.put("schema", row.get("schema"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "FormSchema");
        resource.put("attributes", attributes);
        return resource;
    }
}
