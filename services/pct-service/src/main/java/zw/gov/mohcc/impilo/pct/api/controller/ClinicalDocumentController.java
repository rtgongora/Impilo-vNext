package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.ClinicalDocumentService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ClinicalDocumentEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Clinical documents attached to a patient's care.
 *
 * <p>Serves the paths experience-bff has always called and pct-service has never answered:
 * {@code /v1/records} and {@code /v1/patient/{cpid}/records}. Two live callers depended on them,
 * one citizen-facing, so this is a completion rather than a repoint — nothing in the estate owned
 * the clinical index, only document-service's object store.</p>
 */
@RestController
public class ClinicalDocumentController {

    private final ClinicalDocumentService documentService;

    public ClinicalDocumentController(ClinicalDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/v1/patient/{subjectCpid}/records")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listForPatient(
            @PathVariable String subjectCpid,
            @RequestParam(name = "documentType", required = false) String documentType) {
        List<Map<String, Object>> out = documentService.list(subjectCpid, documentType)
                .stream().map(ClinicalDocumentController::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @GetMapping("/v1/records/{documentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.ok(toMap(documentService.get(documentId)), correlationId()));
    }

    @PostMapping("/v1/records")
    public ResponseEntity<ApiResponse<Map<String, Object>>> index(@RequestBody Map<String, Object> body) {
        ClinicalDocumentEntity d = documentService.index(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(d), correlationId()));
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }

    private static Map<String, Object> toMap(ClinicalDocumentEntity d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getDocumentId().toString());
        m.put("document_id", d.getDocumentId().toString());
        m.put("subject_cpid", d.getSubjectCpid());
        m.put("patient_id", d.getSubjectCpid());
        m.put("journey_id", d.getJourneyId());
        m.put("encounter_id", d.getEncounterId());
        m.put("document_type", d.getDocumentType());
        m.put("title", d.getTitle());
        m.put("description", d.getDescription());
        m.put("document_object_id",
                d.getDocumentObjectId() == null ? null : d.getDocumentObjectId().toString());
        m.put("mime_type", d.getMimeType());
        m.put("file_size", d.getFileSize());
        m.put("storage_key", d.getStorageKey());
        m.put("status", d.getStatus());
        m.put("uploaded_by", d.getUploadedBy());
        m.put("recorded_at", d.getRecordedAt() != null ? d.getRecordedAt().toString() : null);
        m.put("created_at", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        return m;
    }
}
