package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.ClinicalNoteService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ClinicalNoteEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Clinical notes (strangler store) — supports patient-share provenance via optional HTTP headers
 * defined in {@link zw.gov.mohcc.impilo.pct.api.PatientShareProvenanceHeaders}.
 */
@RestController
@RequestMapping("/v1/clinical-notes")
public class ClinicalNoteController {

    private final ClinicalNoteService clinicalNoteService;

    public ClinicalNoteController(ClinicalNoteService clinicalNoteService) {
        this.clinicalNoteService = clinicalNoteService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "patient_id") String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        Page<ClinicalNoteEntity> p = clinicalNoteService.list(tenantId, patientId, page, size);
        List<Map<String, Object>> content = p.getContent().stream().map(this::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(content, TrustContextHolder.require().correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable long id) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        ClinicalNoteEntity row = clinicalNoteService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.ok(toDetail(row), TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        ClinicalNoteEntity row = clinicalNoteService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toDetail(row), TrustContextHolder.require().correlationId().toString()));
    }

    /**
     * Signing is not yet implemented in the PCT strangler store; this endpoint exists so BFF and clients
     * receive a stable response without 404 while a dedicated sign workflow is introduced.
     */
    @PostMapping("/{id}/sign")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sign(@PathVariable long id) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        ClinicalNoteEntity row = clinicalNoteService.get(tenantId, id);
        Map<String, Object> detail = toDetail(row);
        detail.put("signed", false);
        detail.put("signed_at", null);
        return ResponseEntity.ok(ApiResponse.ok(detail, TrustContextHolder.require().correlationId().toString()));
    }

    private Map<String, Object> toSummary(ClinicalNoteEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("patient_id", e.getPatientId());
        m.put("note_type", e.getNoteType());
        m.put("created_at", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        m.put("patient_share_grant_id", e.getPatientShareGrantId());
        m.put("temporary_provider_public_id", e.getTemporaryProviderPublicId());
        return m;
    }

    private Map<String, Object> toDetail(ClinicalNoteEntity e) {
        Map<String, Object> m = new LinkedHashMap<>(toSummary(e));
        m.put("subject_cpid", e.getSubjectCpid() != null ? e.getSubjectCpid().toString() : null);
        m.put("encounter_id", e.getEncounterId() != null ? e.getEncounterId().toString() : null);
        m.put("body", e.getBody());
        m.put("subjective", e.getSubjective());
        m.put("objective", e.getObjective());
        m.put("assessment", e.getAssessment());
        m.put("plan", e.getPlan());
        m.put("author_id", e.getAuthorId());
        m.put("author_name", e.getAuthorName());
        m.put("vito_contribution_id", e.getVitoContributionId());
        m.put("trust_level_at_authoring", e.getTrustLevelAtAuthoring());
        m.put("share_correlation_id", e.getShareCorrelationId());
        m.put("provenance", e.getProvenance());
        return m;
    }
}
