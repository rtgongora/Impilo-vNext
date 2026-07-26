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
import zw.gov.mohcc.impilo.pct.core.clinical.PatientDocumentService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Patient document index API — the patient-anchored record that a stored document belongs to a
 * person's care record. One name for one clinical truth: the experience layer's "records" and
 * "clinical documents" surfaces both read this.
 *
 * <p>Authz: RBAC at ext_authz; the subject relationship is bound inside
 * {@link PatientDocumentService} via {@code ClinicalAccessGuard}, the same discipline as the
 * observation, problem and allergy spines. The optional {@code subject_cpid} constraint on the
 * single-document read is how the citizen-facing path binds a person to their own record.</p>
 */
@RestController
@RequestMapping("/v1/patient-documents")
public class PatientDocumentController {

    private final PatientDocumentService documentService;

    public PatientDocumentController(PatientDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(name = "patientId", required = false) String patientIdCamel,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid,
            @RequestParam(name = "document_type", required = false) String documentType,
            @RequestParam(name = "documentType", required = false) String documentTypeCamel) {
        String subject = firstNonBlank(subjectCpid, patientId, patientIdCamel);
        List<Map<String, Object>> out = documentService
                .list(subject, firstNonBlank(documentType, documentTypeCamel))
                .stream().map(PatientDocumentService::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(
            @PathVariable String documentId,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(
                PatientDocumentService.toMap(documentService.get(documentId, subjectCpid)),
                correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(PatientDocumentService.toMap(documentService.create(body)),
                        correlationId()));
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
