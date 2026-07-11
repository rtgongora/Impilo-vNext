package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DataGovernanceServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.publichealth.PublicHealthGovernanceService;
import zw.gov.mohcc.impilo.experience.publichealth.PublicHealthPreferenceService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public Health BFF Controller — bridges experience UI to
 * surveillance-service, campaigns-service, and indawo-service.
 */
@RestController
@RequestMapping("/internal/v1/public-health")
public class PublicHealthController {

    private static final Logger log = LoggerFactory.getLogger(PublicHealthController.class);
    private final RestTemplate restTemplate;
    private final String surveillanceUrl;
    private final String campaignsUrl;
    private final String indawoUrl;
    private final PublicHealthGovernanceService governance;
    private final PublicHealthPreferenceService preferenceService;
    private final DataGovernanceServiceClient dataGovernanceServiceClient;

    public PublicHealthController(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints,
            PublicHealthGovernanceService governance,
            PublicHealthPreferenceService preferenceService,
            DataGovernanceServiceClient dataGovernanceServiceClient) {
        this.restTemplate = serviceRestTemplate;
        this.surveillanceUrl = endpoints.surveillanceBaseUrl();
        this.campaignsUrl = endpoints.campaignsBaseUrl();
        this.indawoUrl = endpoints.indawoBaseUrl();
        this.governance = governance;
        this.preferenceService = preferenceService;
        this.dataGovernanceServiceClient = dataGovernanceServiceClient;
    }

    // ── Surveillance ─────────────────────────────────────────────

    @GetMapping("/signals")
    public ResponseEntity<Map<String, Object>> listSignals(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/signals", requestId);
    }

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> listCases(
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        String url = surveillanceUrl + "/internal/v1/cases";
        if (status != null && !status.isBlank()) {
            url += "?status=" + URLEncoder.encode(status, StandardCharsets.UTF_8);
        }
        return proxy(url, requestId);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> listAlerts(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/surveillance/alerts", requestId);
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAlert(
            @PathVariable String alertId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/surveillance/alerts/" + URLEncoder.encode(alertId, StandardCharsets.UTF_8) + "/acknowledge",
                requestId,
                Map.of(),
                200);
    }

    @GetMapping("/counters")
    public ResponseEntity<Map<String, Object>> getCounters(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        StringBuilder url = new StringBuilder(surveillanceUrl + "/internal/v1/surveillance/counters");
        boolean first = true;
        if (from != null && !from.isBlank()) {
            url.append(first ? "?" : "&")
                    .append("from=")
                    .append(URLEncoder.encode(from, StandardCharsets.UTF_8));
            first = false;
        }
        if (to != null && !to.isBlank()) {
            url.append(first ? "?" : "&")
                    .append("to=")
                    .append(URLEncoder.encode(to, StandardCharsets.UTF_8));
        }
        return proxy(url.toString(), requestId);
    }

    @PostMapping("/signals")
    public ResponseEntity<Map<String, Object>> createSignal(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/signals", requestId, body, 201);
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestEvent(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/ingest", requestId, body, 202);
    }

    // ── Campaigns ────────────────────────────────────────────────

    @GetMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> listCampaigns(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(campaignsUrl + "/internal/v1/campaigns", requestId);
    }

    /**
     * Public-health programmes (cross-service journey read-path).
     * GET /internal/v1/public-health/programmes?page=&size=
     *
     * <p>Programmes are surfaced from campaigns-service (outreach / public-health programmes
     * are modelled as campaigns); surveillance has no separate programmes registry.</p>
     */
    @GetMapping("/programmes")
    public ResponseEntity<Map<String, Object>> listProgrammes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        String url = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl(campaignsUrl + "/internal/v1/campaigns")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        return proxy(url, requestId);
    }

    @PostMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(campaignsUrl + "/internal/v1/campaigns", requestId, body, 201);
    }

    @GetMapping("/campaigns/{id}")
    public ResponseEntity<Map<String, Object>> getCampaign(
            @PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(campaignsUrl + "/internal/v1/campaigns/" + id, requestId);
    }

    @PostMapping("/campaigns/{id}/dispatch")
    public ResponseEntity<Map<String, Object>> dispatchCampaign(
            @PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedMutate();
        return proxyPost(campaignsUrl + "/internal/v1/campaigns/" + id + "/dispatch", requestId, Map.of(), 200);
    }

    @PostMapping("/campaigns/{id}/close")
    public ResponseEntity<Map<String, Object>> closeCampaign(
            @PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedMutate();
        return proxyPost(campaignsUrl + "/internal/v1/campaigns/" + id + "/close", requestId, Map.of(), 200);
    }

    @PostMapping("/campaigns/{id}/enroll")
    public ResponseEntity<Map<String, Object>> enrollCampaignParticipant(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(campaignsUrl + "/internal/v1/campaigns/" + id + "/enroll", requestId, body, 201);
    }

    // ── Indawo Sites ─────────────────────────────────────────────

    @GetMapping("/sites")
    public ResponseEntity<Map<String, Object>> listSites(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(indawoUrl + "/internal/v1/sites", requestId);
    }

    @GetMapping("/sites/{id}")
    public ResponseEntity<Map<String, Object>> getSite(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(indawoUrl + "/internal/v1/sites/" + id, requestId);
    }

    // ── Indawo Site Registry (Licensing/Inspection Ops) ──────────

    @GetMapping("/site-registry/sites")
    public ResponseEntity<Map<String, Object>> listRegistrySites(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "regulatory_status") String regulatoryStatus,
            @RequestParam(required = false, name = "site_category") String siteCategory,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId
    ) {
        governance.assertGovernedRead();
        String url = indawoUrl + "/internal/v1/site-registry/sites";
        var b = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("page", page)
                .queryParam("size", size);
        if (search != null && !search.isBlank()) b.queryParam("search", search);
        if (regulatoryStatus != null && !regulatoryStatus.isBlank()) b.queryParam("regulatory_status", regulatoryStatus);
        if (siteCategory != null && !siteCategory.isBlank()) b.queryParam("site_category", siteCategory);
        if (province != null && !province.isBlank()) b.queryParam("province", province);
        if (district != null && !district.isBlank()) b.queryParam("district", district);
        return proxy(b.toUriString(), requestId);
    }

    @GetMapping("/site-registry/sites/{siteId}")
    public ResponseEntity<Map<String, Object>> getRegistrySiteProfile(
            @PathVariable String siteId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId
    ) {
        governance.assertGovernedRead();
        return proxy(indawoUrl + "/internal/v1/site-registry/sites/" + siteId, requestId);
    }

    @PutMapping("/site-registry/sites/{siteId}/location")
    public ResponseEntity<Map<String, Object>> updateRegistrySiteLocation(
            @PathVariable String siteId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPut(indawoUrl + "/internal/v1/site-registry/sites/" + siteId + "/location", requestId, body, 200);
    }

    @PostMapping("/site-registry/applications")
    public ResponseEntity<Map<String, Object>> createRegistryApplication(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/applications", requestId, body, 201);
    }

    @PostMapping("/site-registry/applications/{applicationId}/submit")
    public ResponseEntity<Map<String, Object>> submitRegistryApplication(
            @PathVariable String applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/applications/" + applicationId + "/submit", requestId, Map.of(), 200);
    }

    @PostMapping("/site-registry/sites/{siteId}/renewals")
    public ResponseEntity<Map<String, Object>> createRenewal(
            @PathVariable String siteId,
            @RequestParam(defaultValue = "Applicant") String applicantName,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId
    ) {
        governance.assertGovernedMutate();
        String url = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl(indawoUrl + "/internal/v1/site-registry/sites/" + siteId + "/renewals")
                .queryParam("applicantName", applicantName)
                .toUriString();
        return proxyPost(url, requestId, Map.of(), 201);
    }

    @GetMapping("/site-registry/checklist-templates")
    public ResponseEntity<Map<String, Object>> listChecklistTemplates(
            @RequestParam(name = "inspection_type") String inspectionType,
            @RequestParam(name = "site_category", required = false) String siteCategory,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId
    ) {
        governance.assertGovernedRead();
        var b = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(indawoUrl + "/internal/v1/site-registry/checklist-templates")
                .queryParam("inspection_type", inspectionType);
        if (siteCategory != null && !siteCategory.isBlank()) b.queryParam("site_category", siteCategory);
        return proxy(b.toUriString(), requestId);
    }

    @GetMapping("/site-registry/checklist-templates/{templateId}")
    public ResponseEntity<Map<String, Object>> getChecklistTemplate(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId
    ) {
        governance.assertGovernedRead();
        return proxy(indawoUrl + "/internal/v1/site-registry/checklist-templates/" + templateId, requestId);
    }

    @PostMapping("/site-registry/inspections")
    public ResponseEntity<Map<String, Object>> scheduleInspection(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/inspections", requestId, body, 201);
    }

    @GetMapping("/site-registry/inspections")
    public ResponseEntity<Map<String, Object>> listRegistryInspections(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int size
    ) {
        governance.assertGovernedRead();
        var b = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl(indawoUrl + "/internal/v1/site-registry/inspections")
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return proxy(b.toUriString(), requestId);
    }

    @PostMapping("/inspections")
    public ResponseEntity<Map<String, Object>> scheduleInspectionCompatibility(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return scheduleInspection(requestId, body);
    }

    @PostMapping("/site-registry/inspections/{inspectionId}/record")
    public ResponseEntity<Map<String, Object>> recordInspection(
            @PathVariable String inspectionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/inspections/" + inspectionId + "/record", requestId, body, 200);
    }

    @PostMapping("/site-registry/compliance-actions/{actionId}")
    public ResponseEntity<Map<String, Object>> updateComplianceAction(
            @PathVariable String actionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/compliance-actions/" + actionId, requestId, body, 200);
    }

    @GetMapping("/site-registry/compliance-actions")
    public ResponseEntity<Map<String, Object>> listRegistryComplianceActions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int size
    ) {
        governance.assertGovernedRead();
        var b = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl(indawoUrl + "/internal/v1/site-registry/compliance-actions")
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return proxy(b.toUriString(), requestId);
    }

    @PostMapping("/site-registry/licences")
    public ResponseEntity<Map<String, Object>> issueLicence(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/licences", requestId, body, 201);
    }

    @PostMapping("/site-registry/enforcement-cases")
    public ResponseEntity<Map<String, Object>> openEnforcementCase(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/enforcement-cases", requestId, body, 201);
    }

    @GetMapping("/site-registry/enforcement-cases")
    public ResponseEntity<Map<String, Object>> listRegistryEnforcementCases(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int size
    ) {
        governance.assertGovernedRead();
        var b = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl(indawoUrl + "/internal/v1/site-registry/enforcement-cases")
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return proxy(b.toUriString(), requestId);
    }

    @PostMapping(value = "/site-registry/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadRegistryDocument(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("siteId") String siteId,
            @RequestPart("documentType") String documentType,
            @RequestPart(value = "applicationId", required = false) String applicationId,
            @RequestPart(value = "notes", required = false) String notes
    ) {
        governance.assertGovernedMutate();
        try {
            String url = indawoUrl + "/internal/v1/site-registry/documents";

            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin"; }
            });
            parts.add("siteId", siteId);
            parts.add("documentType", documentType);
            if (applicationId != null && !applicationId.isBlank()) parts.add("applicationId", applicationId);
            if (notes != null) parts.add("notes", notes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            JsonNode result = restTemplate.postForEntity(url, new HttpEntity<>(parts, headers), JsonNode.class).getBody();
            Object data = result != null && result.has("data") ? result.get("data") : (result != null ? result : Map.of());
            return ResponseEntity.status(201).body(Map.of("data", data, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.warn("Public health proxy multipart failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId)));
        }
    }

    @PostMapping("/site-registry/documents/{documentId}/verify")
    public ResponseEntity<Map<String, Object>> verifyRegistryDocument(
            @PathVariable String documentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/documents/" + documentId + "/verify", requestId, body, 200);
    }

    @PostMapping("/site-registry/assignments")
    public ResponseEntity<Map<String, Object>> createAssignment(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/assignments", requestId, body, 201);
    }

    @PostMapping("/site-registry/assignments/{assignmentId}")
    public ResponseEntity<Map<String, Object>> updateAssignment(
            @PathVariable String assignmentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        governance.assertGovernedMutate();
        return proxyPost(indawoUrl + "/internal/v1/site-registry/assignments/" + assignmentId, requestId, body, 200);
    }

    @GetMapping("/site-registry/dashboard/summary")
    public ResponseEntity<Map<String, Object>> dashboardSummary(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(indawoUrl + "/internal/v1/site-registry/dashboard/summary", requestId);
    }

    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> workspaceContext(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(value = CompanionHeaders.WORKSPACE_ID, required = false) String workspaceId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestParam(required = false) String jurisdiction_pack) {
        governance.assertGovernedRead();
        String effectivePack = jurisdiction_pack;
        if ((effectivePack == null || effectivePack.isBlank()) && actorId != null && !actorId.isBlank()) {
            effectivePack = preferenceService.loadJurisdictionPack(tenantId, actorId);
        }
        StringBuilder url = new StringBuilder(surveillanceUrl + "/internal/v1/public-health/context");
        boolean first = true;
        if (effectivePack != null && !effectivePack.isBlank()) {
            url.append("?jurisdiction_pack=").append(URLEncoder.encode(effectivePack, StandardCharsets.UTF_8));
            first = false;
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            url.append(first ? "?" : "&").append("workspace_id=").append(URLEncoder.encode(workspaceId, StandardCharsets.UTF_8));
            first = false;
        }
        if (facilityId != null && !facilityId.isBlank()) {
            url.append(first ? "?" : "&").append("facility_id=").append(URLEncoder.encode(facilityId, StandardCharsets.UTF_8));
        }
        return proxy(url.toString(), requestId);
    }

    @GetMapping("/preferences")
    public ResponseEntity<Map<String, Object>> getPreferences(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        String pack = (actorId == null || actorId.isBlank())
                ? "national"
                : preferenceService.loadJurisdictionPack(tenantId, actorId);
        return ResponseEntity.ok(Map.of(
                "data", Map.of("jurisdiction_pack", pack != null ? pack : "national"),
                "meta", Map.of("request_id", requestId)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<Map<String, Object>> putPreferences(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required"),
                    "meta", Map.of("request_id", requestId)));
        }
        String pack = body.get("jurisdiction_pack") == null ? null : String.valueOf(body.get("jurisdiction_pack"));
        return ResponseEntity.ok(Map.of(
                "data", preferenceService.saveJurisdictionPack(tenantId, actorId, pack),
                "meta", Map.of("request_id", requestId)));
    }

    @GetMapping("/operations-home")
    public ResponseEntity<Map<String, Object>> operationsHome(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/operations-home", requestId);
    }

    @GetMapping("/map-markers")
    public ResponseEntity<Map<String, Object>> mapMarkers(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/map-markers", requestId);
    }

    @PostMapping("/nompilo/situation-summary")
    public ResponseEntity<Map<String, Object>> nompiloSituationSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/nompilo/situation-summary", requestId, Map.of(), 200);
    }

    @GetMapping("/intelligence/alert-rules")
    public ResponseEntity<Map<String, Object>> listAlertRules(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/intelligence/alert-rules", requestId);
    }

    @PostMapping("/intelligence/alert-rules")
    public ResponseEntity<Map<String, Object>> createAlertRule(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/intelligence/alert-rules", requestId, body, 201);
    }

    @PostMapping("/intelligence/alert-rules/{ruleId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleAlertRule(
            @PathVariable String ruleId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/intelligence/alert-rules/"
                        + URLEncoder.encode(ruleId, StandardCharsets.UTF_8) + "/toggle",
                requestId,
                body,
                200);
    }

    @GetMapping("/intelligence/data-sources")
    public ResponseEntity<Map<String, Object>> listDataSources(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/intelligence/data-sources", requestId);
    }

    @PostMapping("/intelligence/data-sources")
    public ResponseEntity<Map<String, Object>> upsertDataSource(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/intelligence/data-sources", requestId, body, 201);
    }

    @PostMapping("/intelligence/data-sources/{sourceId}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeatDataSource(
            @PathVariable String sourceId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/intelligence/data-sources/"
                        + URLEncoder.encode(sourceId, StandardCharsets.UTF_8) + "/heartbeat",
                requestId,
                body,
                200);
    }

    @GetMapping("/intelligence/data-quality-issues")
    public ResponseEntity<Map<String, Object>> listDataQualityIssues(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/intelligence/data-quality-issues", requestId);
    }

    @PostMapping("/intelligence/data-quality-issues")
    public ResponseEntity<Map<String, Object>> createDataQualityIssue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/intelligence/data-quality-issues", requestId, body, 201);
    }

    @PostMapping("/intelligence/data-quality-issues/{issueId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveDataQualityIssue(
            @PathVariable String issueId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/intelligence/data-quality-issues/"
                        + URLEncoder.encode(issueId, StandardCharsets.UTF_8) + "/resolve",
                requestId,
                body,
                200);
    }

    @PostMapping("/intelligence/rules/{ruleId}/conditions")
    public ResponseEntity<Map<String, Object>> createRuleCondition(
            @PathVariable String ruleId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/intelligence/rules/"
                        + URLEncoder.encode(ruleId, StandardCharsets.UTF_8) + "/conditions",
                requestId,
                body,
                201);
    }

    @PostMapping("/intelligence/rules/{ruleId}/test")
    public ResponseEntity<Map<String, Object>> testRule(
            @PathVariable String ruleId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/intelligence/rules/"
                        + URLEncoder.encode(ruleId, StandardCharsets.UTF_8) + "/test",
                requestId,
                Map.of(),
                200);
    }

    @PostMapping("/intelligence/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateRule(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/intelligence/evaluate", requestId, body, 200);
    }

    @GetMapping("/intelligence/triggers")
    public ResponseEntity<Map<String, Object>> listRuleTriggers(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/intelligence/triggers", requestId);
    }

    @GetMapping("/intelligence/alerts")
    public ResponseEntity<Map<String, Object>> listIntelligenceAlerts(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/intelligence/alerts", requestId);
    }

    @PostMapping("/intelligence/alerts/{alertId}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeIntelligenceAlert(
            @PathVariable String alertId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/intelligence/alerts/"
                        + URLEncoder.encode(alertId, StandardCharsets.UTF_8) + "/acknowledge",
                requestId,
                Map.of(),
                200);
    }

    @PostMapping("/intelligence/alerts/{alertId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveIntelligenceAlert(
            @PathVariable String alertId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/intelligence/alerts/"
                        + URLEncoder.encode(alertId, StandardCharsets.UTF_8) + "/resolve",
                requestId,
                Map.of(),
                200);
    }

    @PostMapping("/inspections/schedules")
    public ResponseEntity<Map<String, Object>> scheduleInspectionSchedule(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/inspections/schedules", requestId, body, 201);
    }

    @GetMapping("/inspections/schedules")
    public ResponseEntity<Map<String, Object>> listInspectionSchedules(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/inspections/schedules", requestId);
    }

    @GetMapping("/outbreaks")
    public ResponseEntity<Map<String, Object>> listOutbreaks(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/outbreaks", requestId);
    }

    @GetMapping("/complaints")
    public ResponseEntity<Map<String, Object>> listComplaints(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/complaints", requestId);
    }

    @PostMapping("/complaints")
    public ResponseEntity<Map<String, Object>> fileComplaint(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/complaints", requestId, body, 201);
    }

    @PostMapping("/complaints/{complaintId}/transition")
    public ResponseEntity<Map<String, Object>> transitionComplaint(
            @PathVariable String complaintId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/complaints/"
                        + URLEncoder.encode(complaintId, StandardCharsets.UTF_8) + "/transition",
                requestId,
                body,
                200);
    }

    @PostMapping("/outbreaks/{outbreakId}/transition")
    public ResponseEntity<Map<String, Object>> transitionOutbreak(
            @PathVariable String outbreakId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/outbreaks/"
                        + URLEncoder.encode(outbreakId, StandardCharsets.UTF_8) + "/transition",
                requestId,
                body,
                200);
    }

    @GetMapping("/field-tasks")
    public ResponseEntity<Map<String, Object>> listFieldTasks(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/field-tasks", requestId);
    }

    @PostMapping("/field-tasks")
    public ResponseEntity<Map<String, Object>> createFieldTask(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/field-tasks", requestId, body, 201);
    }

    @PostMapping("/field-tasks/{taskId}/transition")
    public ResponseEntity<Map<String, Object>> transitionFieldTask(
            @PathVariable String taskId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/field-tasks/"
                        + URLEncoder.encode(taskId, StandardCharsets.UTF_8) + "/transition",
                requestId,
                body,
                200);
    }

    @GetMapping("/investigations")
    public ResponseEntity<Map<String, Object>> listInvestigations(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/investigations", requestId);
    }

    @PostMapping("/investigations")
    public ResponseEntity<Map<String, Object>> openInvestigation(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/investigations", requestId, body, 201);
    }

    @PostMapping("/investigations/{investigationId}/status")
    public ResponseEntity<Map<String, Object>> updateInvestigationStatus(
            @PathVariable String investigationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/investigations/"
                        + URLEncoder.encode(investigationId, StandardCharsets.UTF_8) + "/status",
                requestId,
                body,
                200);
    }

    @GetMapping("/reports/briefs")
    public ResponseEntity<Map<String, Object>> listBriefs(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return proxy(surveillanceUrl + "/internal/v1/public-health/reports/briefs", requestId);
    }

    @PostMapping("/reports/briefs/generate")
    public ResponseEntity<Map<String, Object>> generateBrief(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedExport();
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/reports/briefs/generate", requestId, body, 201);
    }

    @PostMapping("/reports/briefs/{briefId}/publish")
    public ResponseEntity<Map<String, Object>> publishBrief(
            @PathVariable String briefId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedExport();
        return proxyPost(
                surveillanceUrl + "/internal/v1/public-health/reports/briefs/"
                        + URLEncoder.encode(briefId, StandardCharsets.UTF_8) + "/publish",
                requestId,
                Map.of(),
                200);
    }

    @PostMapping("/reports/briefs/{briefId}/exports/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateBriefExport(
            @PathVariable String briefId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedExport();
        String format = text(firstPresent(body, "format", "file_format"), "PDF").toUpperCase();
        String dataset = "public-health.intelligence-briefs";
        Map<String, Object> exportRequest = new LinkedHashMap<>();
        exportRequest.put("dataset", dataset);
        exportRequest.put("resource_type", "intelligence_brief");
        exportRequest.put("resource_id", briefId);
        exportRequest.put("format", format);
        exportRequest.put("purpose", text(body.get("purpose"), "PUBLIC_HEALTH"));
        exportRequest.put("requested_by", "public-health-bff");
        exportRequest.put("constraints", Map.of("max_rows", 1));
        Object dgResult = dataGovernanceServiceClient.createExport(exportRequest);
        return ResponseEntity.ok(Map.of(
                "data", Map.of(
                        "governance", dgResult,
                        "brief_id", briefId,
                        "format", format,
                        "allowed", true),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/reports/briefs/{briefId}/exports")
    public ResponseEntity<Map<String, Object>> exportBrief(
            @PathVariable String briefId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedExport();
        String format = text(firstPresent(body, "format", "file_format"), "PDF").toUpperCase();
        String dataset = "public-health.intelligence-briefs";
        Map<String, Object> exportRequest = new LinkedHashMap<>();
        exportRequest.put("dataset", dataset);
        exportRequest.put("resource_type", "intelligence_brief");
        exportRequest.put("resource_id", briefId);
        exportRequest.put("format", format);
        exportRequest.put("purpose", text(body.get("purpose"), "PUBLIC_HEALTH"));
        exportRequest.put("requested_by", "public-health-bff");
        exportRequest.put("constraints", Map.of("max_rows", 1));
        try {
            Object dgResultRaw = dataGovernanceServiceClient.createExport(exportRequest);
            Object dgResult = dgResultRaw != null ? dgResultRaw : Map.of("status", "EVALUATED");
            JsonNode exportPayload = restTemplate.postForEntity(
                    surveillanceUrl + "/internal/v1/public-health/reports/briefs/"
                            + URLEncoder.encode(briefId, StandardCharsets.UTF_8) + "/exports",
                    Map.of("format", format),
                    JsonNode.class).getBody();
            if (exportPayload == null) {
                throw new IllegalStateException("Brief export payload empty");
            }
            Object briefExport = exportPayload.has("data") ? exportPayload.get("data") : exportPayload;
            governance.audit(
                    tenantId,
                    correlationId,
                    purposeOfUse,
                    facilityId,
                    "PUBLIC_HEALTH_BRIEF_EXPORT",
                    "EXPORT",
                    "SUCCESS",
                    "INTELLIGENCE_BRIEF",
                    briefId,
                    Map.of(
                            "format", format,
                            "request_id", requestId,
                            "actor_id", actorId != null ? actorId : "unknown"));
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(
                            "governance", dgResult,
                            "export", briefExport),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Public health brief export failed: {}", e.getMessage());
            governance.audit(
                    tenantId,
                    correlationId,
                    purposeOfUse,
                    facilityId,
                    "PUBLIC_HEALTH_BRIEF_EXPORT",
                    "EXPORT",
                    "ERROR",
                    "INTELLIGENCE_BRIEF",
                    briefId,
                    Map.of(
                            "format", format,
                            "request_id", requestId,
                            "error_message", e.getMessage() != null ? e.getMessage() : "unknown"));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "PUBLIC_HEALTH_EXPORT_UNAVAILABLE",
                            "message", "Unable to produce brief export while upstream services are unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/weekly-idsr")
    public ResponseEntity<Map<String, Object>> weeklyIdsr(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        governance.assertGovernedExport();
        StringBuilder url = new StringBuilder(surveillanceUrl + "/internal/v1/public-health/weekly-idsr");
        boolean first = true;
        if (from != null && !from.isBlank()) {
            url.append(first ? "?" : "&")
                    .append("from=")
                    .append(URLEncoder.encode(from, StandardCharsets.UTF_8));
            first = false;
        }
        if (to != null && !to.isBlank()) {
            url.append(first ? "?" : "&")
                    .append("to=")
                    .append(URLEncoder.encode(to, StandardCharsets.UTF_8));
        }
        return proxy(url.toString(), requestId);
    }

    @PostMapping("/outbreaks")
    public ResponseEntity<Map<String, Object>> createOutbreak(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        Object severityRaw = firstPresent(body, "severity", "severityLevel", "riskAssessment");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", text(body.get("name"), "Outbreak"));
        payload.put("description", text(body.get("description"), "Outbreak signal raised from public-health operation"));
        payload.put("eventType", text(firstPresent(body, "eventType", "event_type", "type"), "OUTBREAK"));
        payload.put("conditionField", text(firstPresent(body, "conditionField", "condition_field"), "syndrome_code"));
        payload.put("threshold", intValue(body.get("threshold"), 1));
        payload.put("windowHours", intValue(firstPresent(body, "windowHours", "window_hours"), 24));
        payload.put("severity", normalizeSeverity(text(severityRaw, "HIGH")));
        putIfPresent(payload, "province", firstPresent(body, "province"));
        putIfPresent(payload, "district", firstPresent(body, "district"));
        putIfPresent(payload, "facilityId", firstPresent(body, "facilityId", "facility_id", "linkedSiteId"));
        putIfPresent(payload, "latitude", parseDoubleField(firstPresent(body, "latitude", "gpsLat", "gps_lat")));
        putIfPresent(payload, "longitude", parseDoubleField(firstPresent(body, "longitude", "gpsLng", "gps_lng")));
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/outbreaks", requestId, payload, 201);
    }

    @PostMapping("/field-operations")
    public ResponseEntity<Map<String, Object>> createFieldOperation(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        Object facilityId = firstPresent(body, "facility_id", "facilityId", "linkedSiteId", "siteId");
        Object operationType = firstPresent(body, "operation_type", "operationType", "activityType");
        Object occurredOn = firstPresent(body, "occurred_on", "occurredOn", "activityDate");
        Map<String, Object> payload = Map.of(
                "facilityId", facilityId,
                "operationType", text(operationType, "FIELD_OPERATION"),
                "status", text(body.get("status"), "PLANNED"),
                "occurredOn", text(occurredOn, ""),
                "details", body);
        return proxyPost(surveillanceUrl + "/internal/v1/public-health/field-operations", requestId, payload, 202);
    }

    // ── Helper ───────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> proxy(String url, String requestId) {
        try {
            // URI.create prevents RestTemplate re-encoding the already-encoded builder output
            // (multi-word search params otherwise arrive double-encoded and match nothing).
            JsonNode result = restTemplate.getForEntity(java.net.URI.create(url), JsonNode.class).getBody();
            if (result == null) {
                throw new IllegalStateException("Upstream returned empty response body");
            }
            Object data = result.has("data") ? result.get("data") : result;
            return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.warn("Public health proxy failed for {}: {}", url, e.getMessage());
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of("code", "PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId)));
        }
    }

    private ResponseEntity<Map<String, Object>> proxyPost(String url, String requestId, Map<String, Object> body, int expectedStatus) {
        try {
            JsonNode result = restTemplate.postForEntity(url, body, JsonNode.class).getBody();
            if (result == null) {
                throw new IllegalStateException("Upstream returned empty response body");
            }
            Object data = result.has("data") ? result.get("data") : result;
            return ResponseEntity.status(expectedStatus).body(Map.of("data", data, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.warn("Public health proxy POST failed for {}: {}", url, e.getMessage());
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of("code", "PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId)));
        }
    }

    private ResponseEntity<Map<String, Object>> proxyPut(String url, String requestId, Map<String, Object> body, int expectedStatus) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            JsonNode result = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.PUT,
                    new HttpEntity<>(body, headers),
                    JsonNode.class).getBody();
            if (result == null) {
                throw new IllegalStateException("Upstream returned empty response body");
            }
            Object data = result.has("data") ? result.get("data") : result;
            return ResponseEntity.status(expectedStatus).body(Map.of("data", data, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.warn("Public health proxy PUT failed for {}: {}", url, e.getMessage());
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of("code", "PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId)));
        }
    }

    private int intValue(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(raw.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String text(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private Object firstPresent(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.containsKey(key) && body.get(key) != null) {
                return body.get(key);
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Double parseDoubleField(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeSeverity(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        return switch (value) {
            case "GRADE_0", "INFORMATIONAL", "INFO" -> "INFORMATIONAL";
            case "GRADE_1", "WATCH", "LOW" -> "WATCH";
            case "GRADE_2", "ALERT", "MEDIUM", "MODERATE" -> "ALERT";
            case "GRADE_3", "EMERGENCY", "HIGH", "CRITICAL" -> "EMERGENCY";
            default -> "ALERT";
        };
    }

}
