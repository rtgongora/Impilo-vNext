package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Progressive identity proofing for an authenticated account-shell (Identity Journey
 * Doctrine §6, Wave G; CJ2/CJ3 assurance escalation). The captured document + selfie +
 * liveness artifacts are uploaded to document-service first (opaque references); this
 * endpoint submits those references plus the OCR-extracted MRZ to the Trust Core, which
 * composes the distinct proofing capabilities and either raises assurance, rejects, or
 * routes to a steward review.
 *
 * <p>The citizen-facing response is deliberately coarse — verified / under-review /
 * could-not-validate + next step — and never surfaces per-capability internals or another
 * person's data.</p>
 */
@RestController
@RequestMapping("/internal/v1/identity/patient/proofing")
public class PatientProofingController {

    private static final Logger log = LoggerFactory.getLogger(PatientProofingController.class);

    private final VitoServiceClient vito;

    public PatientProofingController(VitoServiceClient vito) {
        this.vito = vito;
    }

    public record DocumentProofingRequest(
            String healthId, String documentType,
            List<String> mrzLines, Map<String, String> extractedFields,
            String selfieRef, String documentFaceRef, String livenessCaptureRef) {}

    @PostMapping("/document")
    public ResponseEntity<Map<String, Object>> submitDocument(
            @RequestBody DocumentProofingRequest req,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tenantId", tenantId);
            body.put("healthId", req.healthId());
            body.put("documentType", req.documentType());
            body.put("mrzLines", req.mrzLines() == null ? List.of() : req.mrzLines());
            body.put("extractedFields", req.extractedFields() == null ? Map.of() : req.extractedFields());
            body.put("selfieRef", req.selfieRef());
            body.put("documentFaceRef", req.documentFaceRef());
            body.put("livenessCaptureRef", req.livenessCaptureRef());
            body.put("verifierId", actorId != null ? actorId : "self-service");

            JsonNode r = vito.proofingDocumentSelfie(body);
            String disposition = r == null ? "REVIEW_REQUIRED" : r.path("disposition").asText("REVIEW_REQUIRED");

            switch (disposition) {
                case "VERIFIED" -> {
                    out.put("status", "VERIFIED");
                    out.put("assurance", r.path("outcome").asText("DOCUMENT_VERIFIED"));
                    out.put("message", "Your identity document has been verified and your account assurance has been raised.");
                }
                case "REJECTED" -> {
                    out.put("status", "REJECTED");
                    out.put("message", "We could not validate this document. Please re-capture a clear image, or use assisted verification at a facility.");
                }
                default -> {
                    out.put("status", "UNDER_REVIEW");
                    out.put("message", "Your submission was received and is being reviewed. You will be notified once verification is complete.");
                }
            }
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.warn("proofing/document failed: {}", e.getMessage());
            out.put("status", "UNDER_REVIEW");
            out.put("message", "Your submission was received and is being reviewed. You will be notified once verification is complete.");
            return ResponseEntity.ok(out);
        }
    }
}
