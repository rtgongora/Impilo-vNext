package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PacsServiceClient;
import zw.gov.mohcc.impilo.experience.imaging.ImagingAccessPolicyService;
import zw.gov.mohcc.impilo.experience.imaging.ImagingGovernanceService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Governed imaging API for the Experience layer — proxies the PACS adapter and wraps canonical
 * {@code { success, data }} envelopes expected by the clinical UI.
 */
@RestController
@RequestMapping("/internal/v1/imaging")
public class ImagingExperienceController {

    private final PacsServiceClient pacsServiceClient;
    private final ImagingAccessPolicyService imagingAccessPolicyService;
    private final ImagingGovernanceService imagingGovernanceService;
    private final ObjectMapper objectMapper;

    public ImagingExperienceController(
            PacsServiceClient pacsServiceClient,
            ImagingAccessPolicyService imagingAccessPolicyService,
            ImagingGovernanceService imagingGovernanceService,
            ObjectMapper objectMapper) {
        this.pacsServiceClient = pacsServiceClient;
        this.imagingAccessPolicyService = imagingAccessPolicyService;
        this.imagingGovernanceService = imagingGovernanceService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/studies")
    public ResponseEntity<Map<String, Object>> listStudies(
            HttpServletRequest request,
            @RequestParam(name = "patient_cpid", required = false) String patientCpid) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.listStudies(patientCpid);
        audit(request, "IMAGING_STUDY_LIST", "GET:imaging/studies", "SUCCESS",
                "imaging_study", null, Map.of("patient_cpid", patientCpid != null ? patientCpid : ""));
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/studies/search")
    public ResponseEntity<Map<String, Object>> searchStudies(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> body) {
        imagingGovernanceService.assertGovernedRead();
        Map<String, Object> payload = body != null ? body : Map.of();
        JsonNode raw = pacsServiceClient.searchStudies(payload);
        audit(request, "IMAGING_STUDY_SEARCH", "POST:imaging/studies/search", "SUCCESS",
                "imaging_study", null, Map.copyOf(payload));
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/studies/{studyId}")
    public ResponseEntity<Map<String, Object>> getStudy(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(raw, chartPatientCpid);
        audit(request, "IMAGING_STUDY_GET", "GET:imaging/study", "SUCCESS",
                "imaging_study", studyId, Map.of("chart_patient_cpid", chartPatientCpid != null ? chartPatientCpid : ""));
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/studies/{studyId}/series")
    public ResponseEntity<Map<String, Object>> listSeries(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.listSeries(studyId);
        audit(request, "IMAGING_SERIES_LIST", "GET:imaging/series", "SUCCESS",
                "imaging_study", studyId, Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/studies/{studyId}/series/{seriesId}/instances")
    public ResponseEntity<Map<String, Object>> listInstances(
            HttpServletRequest request,
            @PathVariable String studyId,
            @PathVariable String seriesId,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.listInstances(studyId, seriesId);
        audit(request, "IMAGING_INSTANCE_LIST", "GET:imaging/instances", "SUCCESS",
                "imaging_series", seriesId, Map.of("study_id", studyId));
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/studies/{studyId}/correlate")
    public ResponseEntity<Map<String, Object>> correlateStudy(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestBody(required = false) Map<String, Object> body) {
        imagingGovernanceService.assertGovernedMutate();
        Map<String, Object> payload = normalizeCorrelateBody(body);
        JsonNode raw = pacsServiceClient.correlateStudy(studyId, payload);
        audit(request, "IMAGING_STUDY_CORRELATE", "POST:imaging/correlate", "SUCCESS",
                "imaging_study", studyId, Map.copyOf(payload));
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/studies/{studyId}/sync-hierarchy")
    public ResponseEntity<Map<String, Object>> syncHierarchy(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedMutate();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.syncStudyHierarchy(studyId);
        audit(request, "IMAGING_HIERARCHY_SYNC", "POST:imaging/sync-hierarchy", "SUCCESS",
                "imaging_study", studyId, Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/studies/{studyId}/viewer-sessions")
    public ResponseEntity<Map<String, Object>> launchViewer(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        Map<String, Object> payload = body != null ? body : Map.of();
        try {
            imagingGovernanceService.assertViewerLaunch();
            JsonNode study = pacsServiceClient.getStudy(studyId);
            imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
            JsonNode raw = pacsServiceClient.launchViewerSession(studyId, payload);
            audit(request, "IMAGING_VIEWER_LAUNCHED", "POST:imaging/viewer-sessions", "SUCCESS",
                    "imaging_study", studyId, Map.copyOf(payload));
            return ResponseEntity.ok(envelope(raw));
        } catch (ResponseStatusException | org.springframework.security.access.AccessDeniedException ex) {
            Map<String, Object> deniedDetail = new LinkedHashMap<>(payload);
            deniedDetail.put("reason", ex.getMessage());
            deniedDetail.put("chart_patient_cpid", chartPatientCpid != null ? chartPatientCpid : "");
            audit(request, "IMAGING_VIEWER_ACCESS_DENIED", "POST:imaging/viewer-sessions", "DENIED",
                    "imaging_study", studyId, deniedDetail);
            throw ex;
        }
    }

    @GetMapping("/studies/{studyId}/viewer-launch-context")
    public ResponseEntity<Map<String, Object>> viewerLaunchContext(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestParam(name = "viewerType", required = false) String viewerType,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertViewerLaunch();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.viewerLaunchContext(studyId, viewerType);
        audit(request, "IMAGING_VIEWER_CONTEXT", "GET:imaging/viewer-launch-context", "SUCCESS",
                "imaging_study", studyId, Map.of("viewer_type", viewerType != null ? viewerType : ""));
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/ops/status")
    public ResponseEntity<Map<String, Object>> opsStatus(HttpServletRequest request) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.opsStatus();
        audit(request, "IMAGING_OPS_STATUS", "GET:imaging/ops/status", "SUCCESS",
                "imaging_ops", "status", Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/ops/unmatched-studies")
    public ResponseEntity<Map<String, Object>> opsUnmatchedStudies(HttpServletRequest request) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.opsUnmatchedStudies();
        audit(request, "IMAGING_OPS_UNMATCHED", "GET:imaging/ops/unmatched-studies", "SUCCESS",
                "imaging_ops", "unmatched", Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/ops/failed-correlations")
    public ResponseEntity<Map<String, Object>> opsFailedCorrelations(HttpServletRequest request) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.opsFailedCorrelations();
        audit(request, "IMAGING_OPS_FAILED_CORRELATIONS", "GET:imaging/ops/failed-correlations", "SUCCESS",
                "imaging_ops", "failed-correlations", Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/ops/failed-writebacks")
    public ResponseEntity<Map<String, Object>> opsFailedWritebacks(HttpServletRequest request) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.opsFailedWritebacks();
        audit(request, "IMAGING_OPS_FAILED_WRITEBACKS", "GET:imaging/ops/failed-writebacks", "SUCCESS",
                "imaging_ops", "failed-writebacks", Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/ops/failed-writebacks/{outboxId}/retry")
    public ResponseEntity<Map<String, Object>> retryFailedWriteback(HttpServletRequest request, @PathVariable Long outboxId) {
        imagingGovernanceService.assertGovernedMutate();
        JsonNode raw = pacsServiceClient.retryFailedWriteback(outboxId);
        audit(request, "IMAGING_OPS_WRITEBACK_RETRY", "POST:imaging/ops/failed-writebacks/retry", "SUCCESS",
                "imaging_ops", String.valueOf(outboxId), Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/ops/failed-writebacks/retry-all")
    public ResponseEntity<Map<String, Object>> retryAllFailedWritebacks(HttpServletRequest request) {
        imagingGovernanceService.assertGovernedMutate();
        JsonNode raw = pacsServiceClient.retryAllFailedWritebacks();
        audit(request, "IMAGING_OPS_WRITEBACK_RETRY_ALL", "POST:imaging/ops/failed-writebacks/retry-all", "SUCCESS",
                "imaging_ops", "retry-all", Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    /** Reconciliation exceptions queue (status: OPEN | RESOLVED | DISMISSED; default all). */
    @GetMapping("/ops/exceptions")
    public ResponseEntity<Map<String, Object>> opsExceptions(
            HttpServletRequest request,
            @RequestParam(name = "status", required = false) String status) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.opsExceptions(status);
        audit(request, "IMAGING_OPS_EXCEPTIONS", "GET:imaging/ops/exceptions", "SUCCESS",
                "imaging_ops", "exceptions", Map.of("status", status != null ? status : ""));
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/studies/{studyId}/exceptions")
    public ResponseEntity<Map<String, Object>> listStudyExceptions(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.listStudyExceptions(studyId);
        audit(request, "IMAGING_STUDY_EXCEPTIONS", "GET:imaging/study/exceptions", "SUCCESS",
                "imaging_study", studyId, Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    /** Auditable exception resolution — action/note/dismiss are recorded against the actor. */
    @PostMapping("/ops/exceptions/{exceptionId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveException(
            HttpServletRequest request,
            @PathVariable Long exceptionId,
            @RequestBody Map<String, Object> body) {
        imagingGovernanceService.assertGovernedMutate();
        JsonNode raw = pacsServiceClient.resolveException(exceptionId, body);
        audit(request, "IMAGING_EXCEPTION_RESOLVED", "POST:imaging/ops/exceptions/resolve", "SUCCESS",
                "imaging_exception", String.valueOf(exceptionId), Map.copyOf(body));
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/studies/{studyId}/report-links")
    public ResponseEntity<Map<String, Object>> linkReport(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestBody Map<String, Object> body,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedMutate();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.linkReport(studyId, body);
        audit(request, "IMAGING_REPORT_LINKED", "POST:imaging/report-links", "SUCCESS",
                "imaging_study", studyId, Map.copyOf(body));
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/studies/{studyId}/report-links")
    public ResponseEntity<Map<String, Object>> listReportLinks(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.listReportLinks(studyId);
        audit(request, "IMAGING_REPORT_LIST", "GET:imaging/report-links", "SUCCESS",
                "imaging_study", studyId, Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/studies/{studyId}/annotations")
    public ResponseEntity<Map<String, Object>> createAnnotation(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestBody Map<String, Object> body,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedMutate();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.createAnnotation(studyId, body);
        audit(request, "IMAGING_ANNOTATION_CREATED", "POST:imaging/annotations", "SUCCESS",
                "imaging_study", studyId, Map.copyOf(body));
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/studies/{studyId}/annotations")
    public ResponseEntity<Map<String, Object>> listAnnotations(
            HttpServletRequest request,
            @PathVariable String studyId,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.listAnnotations(studyId);
        audit(request, "IMAGING_ANNOTATION_LIST", "GET:imaging/annotations", "SUCCESS",
                "imaging_study", studyId, Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    private void audit(
            HttpServletRequest request,
            String eventType,
            String action,
            String outcome,
            String resourceType,
            String resourceId,
            Map<String, Object> detail) {
        String subject = request.getHeader(CompanionHeaders.SUBJECT_ID);
        imagingGovernanceService.recordImagingAudit(
                request.getHeader(CompanionHeaders.TENANT_ID),
                request.getHeader(CompanionHeaders.CORRELATION_ID),
                request.getHeader(CompanionHeaders.PURPOSE_OF_USE),
                request.getHeader(CompanionHeaders.FACILITY_ID),
                eventType,
                action,
                outcome,
                actorId(),
                actorType(),
                subject,
                resourceType,
                resourceId,
                detail);
    }

    private static String actorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "anonymous";
        }
        Object p = auth.getPrincipal();
        if (p instanceof Jwt jwt) {
            return jwt.getSubject() != null ? jwt.getSubject() : auth.getName();
        }
        return auth.getName();
    }

    private static String actorType() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().isEmpty()) {
            return "UNKNOWN";
        }
        return auth.getAuthorities().iterator().next().getAuthority();
    }

    private Map<String, Object> envelope(JsonNode data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("data", objectMapper.convertValue(data, Object.class));
        return map;
    }

    private static Map<String, Object> normalizeCorrelateBody(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (body != null) {
            out.putAll(body);
        }
        if (!out.containsKey("orosOrderId") && out.containsKey("oros_order_id")) {
            out.put("orosOrderId", out.get("oros_order_id"));
        }
        return out;
    }
}
