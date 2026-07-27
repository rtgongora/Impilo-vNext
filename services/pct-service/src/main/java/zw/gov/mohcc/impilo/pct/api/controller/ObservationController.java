package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.core.clinical.ObservationService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ObservationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discrete observation SoR API.
 *
 * <p>Field names are snake_case to match the contract the BFF already sends; camelCase aliases
 * are accepted on the query parameters and in the body because the form extractor and the
 * offline pack speak the other dialect.</p>
 *
 * <p>Authz: RBAC at ext_authz; the subject relationship is bound inside
 * {@link ObservationService} via {@code ClinicalAccessGuard}, the same discipline as growth,
 * problems and the allergy list.</p>
 */
@RestController
@RequestMapping("/v1/observations")
public class ObservationController {

    private final ObservationService observationService;

    public ObservationController(ObservationService observationService) {
        this.observationService = observationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(name = "patientId", required = false) String patientIdCamel,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "encounter_id", required = false) String encounterId,
            @RequestParam(name = "encounterId", required = false) String encounterIdCamel) {
        String subject = firstNonBlank(subjectCpid, patientId, patientIdCamel);
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }
        List<Map<String, Object>> out =
                observationService.list(subject, code, firstNonBlank(encounterId, encounterIdCamel))
                        .stream().map(ObservationService::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> record(@RequestBody Map<String, Object> body) {
        ObservationEntity row = observationService.record(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ObservationService.toMap(row), correlationId()));
    }

    /**
     * A set of observations taken at one contact — a vitals round, an extracted form. Applied in
     * one transaction so a partially recorded set never reaches the record.
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> recordBatch(
            @RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("observations");
        if (!(raw instanceof List<?> items) || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "observations must be a non-empty array");
        }
        List<Map<String, Object>> bodies = items.stream()
                .map(item -> {
                    if (!(item instanceof Map<?, ?> map)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "each entry in observations must be an object");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    return typed;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> out = observationService.recordBatch(bodies)
                .stream().map(ObservationService::toMap).collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(out, correlationId()));
    }

    /**
     * Withdraw an observation ({@code ENTERED_IN_ERROR}). Deliberately a status transition rather
     * than a DELETE: the SHR and every engine that consumed the value must learn it was wrong, not
     * have never seen it. Idempotent.
     */
    @PostMapping("/{observationId}/void")
    public ResponseEntity<ApiResponse<Map<String, Object>>> voidObservation(
            @PathVariable String observationId,
            @RequestBody(required = false) Map<String, Object> body) {
        Object reason = body == null ? null : body.get("reason");
        ObservationEntity row = observationService.voidObservation(
                observationId, reason == null ? null : reason.toString());
        return ResponseEntity.ok(ApiResponse.ok(ObservationService.toMap(row), correlationId()));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
