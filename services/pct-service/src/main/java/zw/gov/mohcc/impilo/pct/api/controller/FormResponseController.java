package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.forms.FormExtractionService;
import zw.gov.mohcc.impilo.pct.core.forms.FormResponseService;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormExtractedResourceEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResponseEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.visibility.ClinicalVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Encounter form response lifecycle endpoints (PCT SoR for responses). */
@RestController
@RequestMapping("/v1")
public class FormResponseController {

    private final FormResponseService responseService;
    private final FormExtractionService extractionService;

    public FormResponseController(FormResponseService responseService,
                                  FormExtractionService extractionService) {
        this.responseService = responseService;
        this.extractionService = extractionService;
    }

    @PostMapping("/forms/responses")
    public ResponseEntity<ApiResponse<FormResponseEntity>> createDraft(@RequestBody CreateResponseRequest body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        FormResponseEntity r = responseService.createDraft(body.encounterId(), body.formKey(), body.answers());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(r, correlationId));
    }

    @GetMapping("/forms/responses/{responseId}")
    public ResponseEntity<ApiResponse<FormResponseEntity>> get(@PathVariable UUID responseId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ResponseEntity<ApiResponse<FormResponseEntity>> denied = clinicalReadGuard(correlationId);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(ApiResponse.ok(responseService.get(responseId), correlationId));
    }

    @PatchMapping("/forms/responses/{responseId}/answers")
    public ResponseEntity<ApiResponse<FormResponseEntity>> updateAnswers(
            @PathVariable UUID responseId, @RequestBody AnswersRequest body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                responseService.updateAnswers(responseId, body.answers()), correlationId));
    }

    @PostMapping("/forms/responses/{responseId}/submit")
    public ResponseEntity<ApiResponse<FormResponseEntity>> submit(
            @PathVariable UUID responseId, @RequestBody(required = false) AttestationRequest body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                responseService.submit(responseId, body == null ? null : body.attestation()), correlationId));
    }

    @PostMapping("/forms/responses/{responseId}/countersign")
    public ResponseEntity<ApiResponse<FormResponseEntity>> countersign(
            @PathVariable UUID responseId, @RequestBody(required = false) AttestationRequest body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                responseService.countersign(responseId, body == null ? null : body.attestation()), correlationId));
    }

    @PostMapping("/forms/responses/{responseId}/amend")
    public ResponseEntity<ApiResponse<FormResponseEntity>> amend(
            @PathVariable UUID responseId, @RequestBody AmendRequest body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                responseService.amend(responseId, body.answers(), body.reason()), correlationId));
    }

    @PostMapping("/forms/responses/{responseId}/void")
    public ResponseEntity<ApiResponse<FormResponseEntity>> voidResponse(
            @PathVariable UUID responseId, @RequestBody VoidRequest body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                responseService.voidResponse(responseId, body.reason()), correlationId));
    }

    @GetMapping("/encounters/{encounterId}/form-responses")
    public ResponseEntity<ApiResponse<List<FormResponseEntity>>> listForEncounter(@PathVariable String encounterId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ResponseEntity<ApiResponse<FormResponseEntity>> denied = clinicalReadGuard(correlationId);
        if (denied != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                    "VISIBILITY_CLINICAL_DENIED", "Form responses are not permitted for the current visibility profile.",
                    HttpStatus.FORBIDDEN.value(), correlationId));
        }
        return ResponseEntity.ok(ApiResponse.ok(responseService.listForEncounter(encounterId), correlationId));
    }

    /**
     * Extraction provenance for the encounter — where each form-extracted resource was routed
     * (PCT problem/care-plan, OROS order incl. medication requests, BUTANO observation) and its
     * honest status (CONFIRMED / ROUTED / PENDING / FAILED). Read-only cockpit surface.
     */
    @GetMapping("/encounters/{encounterId}/form-extractions")
    public ResponseEntity<ApiResponse<List<FormExtractedResourceEntity>>> listExtractionsForEncounter(
            @PathVariable String encounterId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ResponseEntity<ApiResponse<FormResponseEntity>> denied = clinicalReadGuard(correlationId);
        if (denied != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                    "VISIBILITY_CLINICAL_DENIED", "Form extractions are not permitted for the current visibility profile.",
                    HttpStatus.FORBIDDEN.value(), correlationId));
        }
        return ResponseEntity.ok(ApiResponse.ok(extractionService.listForEncounter(encounterId), correlationId));
    }

    private ResponseEntity<ApiResponse<FormResponseEntity>> clinicalReadGuard(String correlationId) {
        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        if (ClinicalVisibilityGuard.deniesClinicalRead(vis)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                    "VISIBILITY_CLINICAL_DENIED", "Form response is not permitted for the current visibility profile.",
                    HttpStatus.FORBIDDEN.value(), correlationId));
        }
        return null;
    }

    // --- Request bodies ---
    public record CreateResponseRequest(Long encounterId, String formKey, Map<String, Object> answers) {}
    public record AnswersRequest(Map<String, Object> answers) {}
    public record AttestationRequest(String attestation) {}
    public record AmendRequest(Map<String, Object> answers, String reason) {}
    public record VoidRequest(String reason) {}
}
