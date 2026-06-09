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
import zw.gov.mohcc.impilo.experience.shr.ShrArtifactLinkageRecord;
import zw.gov.mohcc.impilo.experience.shr.ShrArtifactLinkageService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * SHR / Butano artifact linkage intent — persisted in BFF until sovereign write API ships.
 */
@RestController
@RequestMapping("/internal/v1/clinical/shr-artifacts")
public class ClinicalShrArtifactController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalShrArtifactController.class);

    private final ShrArtifactLinkageService linkageService;

    public ClinicalShrArtifactController(ShrArtifactLinkageService linkageService) {
        this.linkageService = linkageService;
    }

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

        ShrArtifactLinkageRecord saved = linkageService.recordIntent(
                tenantId, patientId, artifactType, body, correlationId, requestId);

        log.info(
                "SHR_ARTIFACT_LINKAGE_RECORDED id={} type={} patient_id_len={}",
                saved.getId(), artifactType, patientId.length());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", true);
        envelope.put("code", "SHR_LINKAGE_QUEUED");
        envelope.put("message",
                "Linkage intent recorded; sovereign Butano write will reconcile when enabled.");
        envelope.put("linkage_id", saved.getId().toString());
        envelope.put("status", saved.getStatus());
        envelope.put("detail", Map.of(
                "artifact_type", artifactType,
                "next_step", "Butano write reconciliation per docs/architecture/PACS_DICOM_PIPELINE.md"));
        if (correlationId != null) {
            envelope.put("correlation_id", correlationId);
        }
        if (requestId != null) {
            envelope.put("request_id", requestId);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(envelope);
    }

    private static String stringField(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }
}
