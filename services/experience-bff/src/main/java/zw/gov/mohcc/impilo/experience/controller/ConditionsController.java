package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The patient's problem list, as the experience layer names it.
 *
 * <ul>
 *   <li>GET  /internal/v1/conditions?patient_id= — the complete problem list</li>
 *   <li>POST /internal/v1/conditions — add a problem</li>
 *   <li>POST /internal/v1/conditions/{id}/resolve — resolve a problem</li>
 * </ul>
 *
 * <p><strong>One record, two names.</strong> pct-service owns the problem list
 * ({@code pct_problems}, served at {@code /v1/problems}) and "problem" is the doctrine word.
 * The UI was built on "condition" and reads a {@code ConditionResource}. Rather than add a
 * second endpoint to the system of record — which is how a clinical truth ends up with two
 * systems that disagree — the translation lives here, in the composition layer, in one place.</p>
 *
 * <p>This controller is deliberately unpaged. A problem list is read whole; a page boundary
 * silently omits diagnoses and there is no safe place to cut one.</p>
 */
@RestController
@RequestMapping("/internal/v1/conditions")
public class ConditionsController {

    private static final Logger log = LoggerFactory.getLogger(ConditionsController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Code systems whose codes may be surfaced to the UI as {@code icdCode}. A SNOMED or local
     * code shown in a field labelled ICD is a mislabelled code, and a mislabelled code is how a
     * problem gets counted into the wrong programme or claim.
     */
    private static final List<String> ICD_SYSTEMS = List.of("ICD-10", "ICD10", "ICD-11", "ICD11");

    private final PctServiceClient pctClient;

    public ConditionsController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    /**
     * The create payload, accepting both spellings of every field.
     *
     * <p>The aliases are not cosmetic. The web client posts camelCase ({@code patientId},
     * {@code conditionName}) and this record declared only snake_case, with no naming strategy
     * configured on either side and no key transformation in the api-client — so Jackson bound
     * every field to null and {@code @Valid} rejected the request as a 400 before it ever reached
     * pct-service. That is a second, independent break in the same vertical as the wrong endpoint
     * path: the write path could not have worked even once the path was fixed.</p>
     *
     * <p>Both spellings are accepted rather than one chosen, because the web client and the mobile
     * client do not agree with each other and breaking either to tidy the contract would trade one
     * dead write path for another.</p>
     */
    public record CreateConditionRequest(
            @JsonAlias("patientId") @NotBlank String patient_id,
            @JsonAlias("encounterId") String encounter_id,
            @JsonAlias("journeyId") String journey_id,
            @JsonAlias("conditionName") @NotBlank String condition_name,
            @JsonAlias("icdCode") String icd_code,
            String category,
            @JsonAlias("clinicalStatus") String clinical_status,
            String severity,
            @JsonAlias({"diagnosticCertainty"}) String diagnostic_certainty,
            @JsonAlias("onsetDate") LocalDate onset_date,
            @JsonAlias("recordedBy") String recorded_by,
            String notes,
            @JsonAlias("duplicateResolution") String duplicate_resolution
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listConditions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "patient_id_required",
                    "message", "A problem list can only be read for a named patient.",
                    "meta", meta(requestId, correlationId)));
        }
        try {
            JsonNode pctData = pctClient.listProblems(patientId);
            return ResponseEntity.ok(Map.of(
                    "data", toConditionResources(pctData),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            // An empty 200 here reads as "this patient has no diagnosed conditions", which is an
            // affirmative clinical finding and would be a fabricated one. Fail loudly instead.
            log.error("PCT listProblems failed for patient={}: {}", patientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "condition_list_unavailable",
                    "message", "Conditions could not be retrieved. Do not treat this as an "
                               + "absence of conditions.",
                    "meta", meta(requestId, correlationId)));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCondition(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateConditionRequest request) {
        try {
            JsonNode created = pctClient.createProblem(toPctProblem(request));
            log.info("PCT problem created for patient={}", request.patient_id());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", toConditionResource(created),
                    "meta", meta(requestId, correlationId)));
        } catch (HttpClientErrorException.Conflict conflict) {
            // Not a failure — a question. PCT found this problem already on the patient's list and
            // is asking whether it is the same one returning or a distinct one. Flattening it to a
            // 502 would tell the clinician the write broke and lose the problem they need to see.
            log.info("PCT reported a duplicate problem for patient={}", request.patient_id());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(duplicateBody(conflict, requestId, correlationId));
        } catch (Exception e) {
            // A write that failed must never return 2xx. The clinician has to know the diagnosis
            // they just recorded is not on the record, while they are still in front of the patient.
            log.error("PCT createProblem failed for patient={}: {}", request.patient_id(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "condition_not_recorded",
                    "message", "The condition was not saved and is not on the patient's record. "
                               + "Record it again.",
                    "meta", meta(requestId, correlationId)));
        }
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveCondition(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        try {
            JsonNode result = pctClient.resolveProblem(id.toString());
            log.info("PCT problem resolved: {}", id);
            return ResponseEntity.ok(Map.of(
                    "data", toConditionResource(result),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT resolveProblem failed for problem={}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "condition_not_resolved",
                    "message", "The condition was not resolved and remains active on the "
                               + "patient's record.",
                    "meta", meta(requestId, correlationId)));
        }
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of("request_id", requestId, "correlation_id", correlationId);
    }

    /**
     * Re-presents PCT's duplicate report in the experience layer's vocabulary, so the UI can show
     * the clinician the problem already on the list and the two answers available.
     */
    private static Map<String, Object> duplicateBody(
            HttpClientErrorException conflict, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "condition_already_on_list");
        JsonNode upstream = null;
        try {
            upstream = MAPPER.readTree(conflict.getResponseBodyAsString());
        } catch (Exception ignored) {
            // Fall through to the generic message rather than failing the response.
        }
        body.put("message", upstream != null && upstream.hasNonNull("message")
                ? upstream.get("message").asText()
                : "This condition is already on the patient's list.");
        if (upstream != null && upstream.has("existing")) {
            body.put("existing", toConditionResource(upstream.get("existing")));
        }
        body.put("resolutions", List.of(
                Map.of("duplicate_resolution", "SAME_PROBLEM_RETURNING",
                        "effect", "Marks the existing condition as having recurred. No new entry is created."),
                Map.of("duplicate_resolution", "DISTINCT_PROBLEM",
                        "effect", "Records this as a separate condition alongside the existing one.")));
        body.put("meta", meta(requestId, correlationId));
        return body;
    }

    // ── Translation: experience vocabulary ⇄ PCT problem record ─────────────────

    /**
     * {@code recorded_by} is deliberately not forwarded. PCT stamps the author from the trust
     * context, which is authenticated; a client-supplied author is forgeable and would let the
     * experience layer attribute a diagnosis to a clinician who never made it.
     */
    private static Map<String, Object> toPctProblem(CreateConditionRequest r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject_cpid", r.patient_id());
        body.put("encounter_id", r.encounter_id());
        body.put("journey_id", r.journey_id());
        body.put("display", r.condition_name());
        if (r.icd_code() != null && !r.icd_code().isBlank()) {
            body.put("code", r.icd_code());
            body.put("code_system", "ICD-10");
        }
        body.put("category", r.category());
        body.put("clinical_status", r.clinical_status() != null ? r.clinical_status() : "ACTIVE");
        body.put("severity", r.severity());
        body.put("diagnostic_certainty", r.diagnostic_certainty());
        body.put("onset_date", r.onset_date() != null ? r.onset_date().toString() : null);
        body.put("notes", r.notes());
        body.put("duplicate_resolution", r.duplicate_resolution());
        return body;
    }

    private static List<Map<String, Object>> toConditionResources(JsonNode pctData) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (pctData != null && pctData.isArray()) {
            pctData.forEach(node -> out.add(toConditionResource(node)));
        }
        return out;
    }

    /**
     * Maps one PCT problem onto the {@code ConditionResource} the UI declares. Both the coded
     * value and its system travel, so a reader can tell a coded problem from an uncoded one
     * rather than inferring it from a blank field.
     */
    private static Map<String, Object> toConditionResource(JsonNode p) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (p == null || p.isNull()) {
            return Map.of("id", "", "type", "condition", "attributes", attrs);
        }
        String code = text(p, "code");
        String codeSystem = text(p, "code_system");

        attrs.put("patientId", text(p, "subject_cpid"));
        attrs.put("encounterId", text(p, "encounter_id"));
        attrs.put("journeyId", text(p, "journey_id"));
        attrs.put("conditionName", text(p, "display"));
        attrs.put("code", code);
        attrs.put("codeSystem", codeSystem);
        attrs.put("icdCode", isIcd(codeSystem) ? code : null);
        attrs.put("category", text(p, "category"));
        attrs.put("clinicalStatus", text(p, "clinical_status"));
        attrs.put("severity", text(p, "severity"));
        attrs.put("diagnosticCertainty", text(p, "diagnostic_certainty"));
        attrs.put("onsetDate", text(p, "onset_date"));
        attrs.put("recordedBy", text(p, "recorded_by"));
        attrs.put("resolvedAt", text(p, "resolved_at"));
        attrs.put("notes", text(p, "notes"));
        attrs.put("createdAt", text(p, "created_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", text(p, "problem_id"));
        resource.put("type", "condition");
        resource.put("attributes", attrs);
        return resource;
    }

    private static boolean isIcd(String codeSystem) {
        if (codeSystem == null) return false;
        String normalised = codeSystem.trim().toUpperCase(Locale.ROOT);
        return ICD_SYSTEMS.contains(normalised);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
