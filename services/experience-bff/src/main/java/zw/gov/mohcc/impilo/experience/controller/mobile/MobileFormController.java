package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.FormsServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

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

    private static final Logger log = LoggerFactory.getLogger(MobileFormController.class);

    private final ObjectMapper objectMapper;
    private final FormsServiceClient formsClient;
    private final PctServiceClient pctClient;

    public MobileFormController(ObjectMapper objectMapper, FormsServiceClient formsClient,
                                PctServiceClient pctClient) {
        this.objectMapper = objectMapper;
        this.formsClient = formsClient;
        this.pctClient = pctClient;
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
        String formKey = resolveFormKey(id.toString());
        Map<String, Object> response = persistViaPct(formKey, id.toString(), request.encounter_id(),
                request.patient_id(), request.submitted_by(), request.form_data(), requestId, correlationId);
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
        String formKey = resolveFormKey(request.form_id());
        Map<String, Object> response = persistViaPct(formKey, request.form_id(), request.encounter_id(),
                request.patient_id(), request.submitted_by(), request.form_data(), requestId, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Persist a mobile form submission through the canonical PCT response path (draft → submit), so the
     * submission is a real version-locked, extractable, audited encounter form response — not a throwaway
     * client UUID. The response envelope stays backward-compatible (id = the persisted PCT responseId).
     * Falls back to an unpersisted envelope only if PCT/forms are unavailable, so the mobile flow degrades
     * rather than fails.
     */
    private Map<String, Object> persistViaPct(String formKey, String formId, String encounterId,
                                              String patientId, String submittedBy,
                                              Map<String, Object> formData, String requestId, String correlationId) {
        OffsetDateTime now = OffsetDateTime.now();
        String status = "SUBMITTED";
        String persistedId = null;
        try {
            if (formKey == null || formKey.isBlank()) {
                throw new IllegalStateException("no formKey for schema " + formId);
            }
            Map<String, Object> draftBody = new LinkedHashMap<>();
            draftBody.put("encounterId", parseEncounterId(encounterId));
            draftBody.put("formKey", formKey);
            draftBody.put("answers", formData);
            JsonNode draft = pctClient.createFormResponse(draftBody);
            String responseId = draft != null && draft.hasNonNull("responseId")
                    ? draft.get("responseId").asText() : null;
            if (responseId != null) {
                JsonNode submitted = pctClient.submitFormResponse(responseId, Map.of());
                persistedId = responseId;
                if (submitted != null && submitted.hasNonNull("status")) {
                    status = submitted.get("status").asText();
                }
            }
        } catch (Exception e) {
            log.warn("Mobile form submit could not persist via PCT (form={}, encounter={}): {} — returning "
                    + "unpersisted envelope", formId, encounterId, e.getMessage());
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("form_id", formId);
        attributes.put("encounter_id", encounterId);
        attributes.put("patient_id", patientId);
        attributes.put("submitted_by", submittedBy);
        attributes.put("form_data", formData);
        attributes.put("status", status);
        attributes.put("persisted", persistedId != null);
        attributes.put("submitted_at", now);
        attributes.put("created_at", now);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", persistedId != null ? persistedId : UUID.randomUUID().toString());
        data.put("type", "FormSubmission");
        data.put("attributes", attributes);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }

    private String resolveFormKey(String schemaId) {
        try {
            JsonNode schema = formsClient.getSchema(schemaId);
            if (schema != null && schema.hasNonNull("formKey")) {
                return schema.get("formKey").asText();
            }
        } catch (Exception e) {
            log.warn("Could not resolve formKey for schema {}: {}", schemaId, e.getMessage());
        }
        return null;
    }

    private Object parseEncounterId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return raw;
        }
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
