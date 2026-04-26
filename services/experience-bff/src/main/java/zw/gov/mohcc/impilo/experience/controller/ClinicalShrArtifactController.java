package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Planned SHR / Butano artifact ingest — contract stub for Experience.
 *
 * <p>Butano's HTTP client in this BFF is currently read-only (IPS + visit summaries).
 * This endpoint records the <strong>intent</strong> (structured audit log) and returns
 * {@code 501 NOT IMPLEMENTED} so UIs can exercise the flow without fabricating success.</p>
 *
 * <p>When Butano exposes a write API, replace the body of this method with a forward +
 * idempotent persistence and return {@code 202 ACCEPTED} or {@code 201 CREATED}.</p>
 */
@RestController
@RequestMapping("/internal/v1/clinical/shr-artifacts")
public class ClinicalShrArtifactController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalShrArtifactController.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "IMAGING_STUDY",
            "REFERRAL_PACKAGE",
            "LAB_RESULT_PACKAGE",
            "TELECONSULT_SUMMARY");

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> appendArtifact(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body) {
        String patientId = stringField(body, "patient_id");
        if (patientId == null || patientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }
        String artifactType = stringField(body, "artifact_type");
        if (artifactType == null || !ALLOWED_TYPES.contains(artifactType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "artifact_type must be one of " + ALLOWED_TYPES);
        }

        String tenantId = request.getHeader(CompanionHeaders.TENANT_ID);
        String correlationId = request.getHeader(CompanionHeaders.CORRELATION_ID);
        String requestId = request.getHeader(CompanionHeaders.REQUEST_ID);

        log.warn(
                "SHR_ARTIFACT_REQUEST tenant={} correlationId={} requestId={} type={} patient_id_len={} keys={}",
                tenantId != null ? tenantId : "",
                correlationId != null ? correlationId : "",
                requestId != null ? requestId : "",
                artifactType,
                patientId.length(),
                body.keySet());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", false);
        envelope.put("code", "SHR_WRITE_NOT_IMPLEMENTED");
        envelope.put("message",
                "Butano/SHR artifact ingest is not enabled in this BFF build; request was logged for audit.");
        envelope.put("detail", Map.of(
                "artifact_type", artifactType,
                "next_step", "Implement Butano write + PCT/SHR correlation per docs/audits/service-surfacing-audit.md"));
        if (correlationId != null) {
            envelope.put("correlation_id", correlationId);
        }
        if (requestId != null) {
            envelope.put("request_id", requestId);
        }
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(envelope);
    }

    private static String stringField(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }
}
