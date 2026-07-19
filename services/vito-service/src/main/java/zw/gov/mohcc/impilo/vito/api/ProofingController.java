package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.proofing.ProofingOrchestrator;
import zw.gov.mohcc.impilo.vito.core.proofing.ProofingSubmissionService;
import zw.gov.mohcc.impilo.vito.core.proofing.engine.DocumentEvidence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Progressive-proofing intake (Identity Journey Doctrine §6, Wave G). INTERNAL-only —
 * only the Trust Core (BFF) submits a proofing attempt after uploading the captured
 * artifacts to document-service; VITO composes the distinct proofing capabilities,
 * records the outcome into the proofing ledger, and opens a steward review when a remote
 * attempt cannot auto-verify. Raw image bytes never transit here — only opaque
 * document-service references and the OCR-extracted MRZ.
 */
@RestController
@RequestMapping("/v1/proofing")
public class ProofingController {

    private final ProofingSubmissionService submissionService;

    public ProofingController(ProofingSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    public record DocumentSelfieRequest(
            UUID tenantId, UUID healthId, String documentType,
            List<String> mrzLines, Map<String, String> extractedFields,
            String selfieRef, String documentFaceRef, String livenessCaptureRef,
            String verifierId) {}

    @PostMapping("/document-selfie")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitDocumentSelfie(
            @RequestBody DocumentSelfieRequest req) {
        internalOnly();
        DocumentEvidence evidence = new DocumentEvidence(
                req.documentType(), req.mrzLines(), req.extractedFields(),
                req.selfieRef() == null ? List.of() : List.of(req.selfieRef()));
        ProofingSubmissionService.SubmissionResult result = submissionService.submitDocumentSelfie(
                req.tenantId(), req.healthId(), evidence,
                req.selfieRef(), req.documentFaceRef(), req.livenessCaptureRef(),
                req.verifierId() == null ? "system" : req.verifierId());

        ProofingOrchestrator.ProofingDecision d = result.outcome();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("disposition", d.disposition().name());
        body.put("outcome", d.outcome() == null ? null : d.outcome().name());
        body.put("assuranceLevel", d.assuranceLevel());
        body.put("reason", d.reason());
        body.put("signals", d.signals().stream().map(s -> Map.of(
                "capability", s.capability(), "result", s.result(), "confidence", s.confidence())).toList());
        if (result.reviewId() != null) {
            body.put("reviewId", result.reviewId().toString());
        }
        return ResponseEntity.ok(ApiResponse.ok(body, null));
    }

    private void internalOnly() {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "proofing intake is INTERNAL-only");
        }
    }
}
