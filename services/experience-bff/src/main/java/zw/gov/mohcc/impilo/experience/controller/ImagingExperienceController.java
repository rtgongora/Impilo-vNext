package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
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
    private final PctServiceClient pctServiceClient;
    private final ImagingAccessPolicyService imagingAccessPolicyService;
    private final ImagingGovernanceService imagingGovernanceService;
    private final ObjectMapper objectMapper;

    public ImagingExperienceController(
            PacsServiceClient pacsServiceClient,
            PctServiceClient pctServiceClient,
            ImagingAccessPolicyService imagingAccessPolicyService,
            ImagingGovernanceService imagingGovernanceService,
            ObjectMapper objectMapper) {
        this.pacsServiceClient = pacsServiceClient;
        this.pctServiceClient = pctServiceClient;
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
        imagingGovernanceService.assertViewerLaunch();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        Map<String, Object> payload = body != null ? body : Map.of();
        JsonNode raw = pacsServiceClient.launchViewerSession(studyId, payload);
        audit(request, "IMAGING_VIEWER_LAUNCHED", "POST:imaging/viewer-sessions", "SUCCESS",
                "imaging_study", studyId, Map.copyOf(payload));
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

    @PostMapping("/studies/{studyId}/series/{seriesId}/instances/{instanceId}/edits")
    public ResponseEntity<Map<String, Object>> saveImagingEdit(
            HttpServletRequest request,
            @PathVariable String studyId,
            @PathVariable String seriesId,
            @PathVariable String instanceId,
            @RequestBody Map<String, Object> body,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedMutate();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.saveImagingEdit(studyId, seriesId, instanceId, body);
        Map<String, Object> triageLinkPayload = new LinkedHashMap<>();
        triageLinkPayload.put("journey_id", body.get("journey_id"));
        triageLinkPayload.put("triage_record_id", body.get("triage_record_id"));
        triageLinkPayload.put("study_id", Long.parseLong(studyId));
        triageLinkPayload.put("series_id", Long.parseLong(seriesId));
        triageLinkPayload.put("instance_id", Long.parseLong(instanceId));
        if (raw.has("id")) {
            triageLinkPayload.put("imaging_edit_id", raw.get("id").asLong());
        }
        pctServiceClient.createTriageImagingLink(triageLinkPayload);
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/studies/{studyId}/series/{seriesId}/instances/{instanceId}/edits")
    public ResponseEntity<Map<String, Object>> listImagingEdits(
            @PathVariable String studyId,
            @PathVariable String seriesId,
            @PathVariable String instanceId,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.listImagingEdits(studyId, seriesId, instanceId);
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/studies/{studyId}/edits")
    public ResponseEntity<Map<String, Object>> saveStudyImagingEdit(
            @PathVariable String studyId,
            @RequestBody Map<String, Object> body,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedMutate();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.saveStudyImagingEdit(studyId, body);
        Map<String, Object> triageLinkPayload = new LinkedHashMap<>();
        triageLinkPayload.put("journey_id", body.get("journey_id"));
        triageLinkPayload.put("triage_record_id", body.get("triage_record_id"));
        triageLinkPayload.put("study_id", Long.parseLong(studyId));
        if (raw.has("id")) triageLinkPayload.put("imaging_edit_id", raw.get("id").asLong());
        pctServiceClient.createTriageImagingLink(triageLinkPayload);
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/studies/{studyId}/edits")
    public ResponseEntity<Map<String, Object>> listStudyImagingEdits(
            @PathVariable String studyId,
            @RequestParam(name = "chart_patient_cpid", required = false) String chartPatientCpid) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode study = pacsServiceClient.getStudy(studyId);
        imagingAccessPolicyService.assertStudyMatchesPatient(study, chartPatientCpid);
        JsonNode raw = pacsServiceClient.listStudyImagingEdits(studyId);
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
