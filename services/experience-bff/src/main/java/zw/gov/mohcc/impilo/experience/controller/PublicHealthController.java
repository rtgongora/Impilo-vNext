package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

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

    public PublicHealthController(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.surveillanceUrl = endpoints.surveillanceBaseUrl();
        this.campaignsUrl = endpoints.campaignsBaseUrl();
        this.indawoUrl = endpoints.indawoBaseUrl();
    }

    // ── Surveillance ─────────────────────────────────────────────

    @GetMapping("/signals")
    public ResponseEntity<Map<String, Object>> listSignals(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(surveillanceUrl + "/internal/v1/signals", requestId);
    }

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> listCases(
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        String url = surveillanceUrl + "/internal/v1/cases";
        if (status != null && !status.isBlank()) {
            url += "?status=" + URLEncoder.encode(status, StandardCharsets.UTF_8);
        }
        return proxy(url, requestId);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> listAlerts(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(surveillanceUrl + "/internal/v1/surveillance/alerts", requestId);
    }

    @GetMapping("/counters")
    public ResponseEntity<Map<String, Object>> getCounters(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
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
        try {
            JsonNode result = restTemplate.postForEntity(
                            surveillanceUrl + "/internal/v1/signals", body, JsonNode.class)
                    .getBody();
            return ResponseEntity.status(201)
                    .body(Map.of("data", result != null ? result : Map.of(), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.error("Signal creation failed: {}", e.getMessage());
            return ResponseEntity.status(400)
                    .body(Map.of("error", Map.of("code", "FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestEvent(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode result = restTemplate.postForEntity(
                            surveillanceUrl + "/internal/v1/ingest", body, JsonNode.class)
                    .getBody();
            return ResponseEntity.status(202)
                    .body(Map.of("data", result != null ? result : Map.of(), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.error("Ingest failed: {}", e.getMessage());
            return ResponseEntity.status(400)
                    .body(Map.of("error", Map.of("code", "INGEST_FAILED", "message", e.getMessage())));
        }
    }

    // ── Campaigns ────────────────────────────────────────────────

    @GetMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> listCampaigns(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(campaignsUrl + "/internal/v1/campaigns", requestId);
    }

    @PostMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode result = restTemplate.postForEntity(campaignsUrl + "/internal/v1/campaigns", body, JsonNode.class).getBody();
            return ResponseEntity.status(201).body(Map.of("data", result != null ? result : Map.of(), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.error("Campaign creation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/campaigns/{id}")
    public ResponseEntity<Map<String, Object>> getCampaign(
            @PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(campaignsUrl + "/internal/v1/campaigns/" + id, requestId);
    }

    @PostMapping("/campaigns/{id}/dispatch")
    public ResponseEntity<Map<String, Object>> dispatchCampaign(
            @PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode result = restTemplate.postForEntity(campaignsUrl + "/internal/v1/campaigns/" + id + "/dispatch", Map.of(), JsonNode.class).getBody();
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of(), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "DISPATCH_FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/campaigns/{id}/close")
    public ResponseEntity<Map<String, Object>> closeCampaign(
            @PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode result = restTemplate.postForEntity(
                            campaignsUrl + "/internal/v1/campaigns/" + id + "/close", Map.of(), JsonNode.class)
                    .getBody();
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of(), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.error("Campaign close failed: {}", e.getMessage());
            return ResponseEntity.status(400)
                    .body(Map.of("error", Map.of("code", "CLOSE_FAILED", "message", e.getMessage())));
        }
    }

    // ── Indawo Sites ─────────────────────────────────────────────

    @GetMapping("/sites")
    public ResponseEntity<Map<String, Object>> listSites(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(indawoUrl + "/internal/v1/sites", requestId);
    }

    @GetMapping("/sites/{id}")
    public ResponseEntity<Map<String, Object>> getSite(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
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
        return proxy(indawoUrl + "/internal/v1/site-registry/sites/" + siteId, requestId);
    }

    @PostMapping("/site-registry/applications")
    public ResponseEntity<Map<String, Object>> createRegistryApplication(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/applications", requestId, body, 201);
    }

    @PostMapping("/site-registry/applications/{applicationId}/submit")
    public ResponseEntity<Map<String, Object>> submitRegistryApplication(
            @PathVariable String applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/applications/" + applicationId + "/submit", requestId, Map.of(), 200);
    }

    @PostMapping("/site-registry/sites/{siteId}/renewals")
    public ResponseEntity<Map<String, Object>> createRenewal(
            @PathVariable String siteId,
            @RequestParam(defaultValue = "Applicant") String applicantName,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId
    ) {
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
        return proxy(indawoUrl + "/internal/v1/site-registry/checklist-templates/" + templateId, requestId);
    }

    @PostMapping("/site-registry/inspections")
    public ResponseEntity<Map<String, Object>> scheduleInspection(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/inspections", requestId, body, 201);
    }

    @PostMapping("/site-registry/inspections/{inspectionId}/record")
    public ResponseEntity<Map<String, Object>> recordInspection(
            @PathVariable String inspectionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/inspections/" + inspectionId + "/record", requestId, body, 200);
    }

    @PostMapping("/site-registry/compliance-actions/{actionId}")
    public ResponseEntity<Map<String, Object>> updateComplianceAction(
            @PathVariable String actionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/compliance-actions/" + actionId, requestId, body, 200);
    }

    @PostMapping("/site-registry/licences")
    public ResponseEntity<Map<String, Object>> issueLicence(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/licences", requestId, body, 201);
    }

    @PostMapping("/site-registry/enforcement-cases")
    public ResponseEntity<Map<String, Object>> openEnforcementCase(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/enforcement-cases", requestId, body, 201);
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
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/site-registry/documents/{documentId}/verify")
    public ResponseEntity<Map<String, Object>> verifyRegistryDocument(
            @PathVariable String documentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/documents/" + documentId + "/verify", requestId, body, 200);
    }

    @PostMapping("/site-registry/assignments")
    public ResponseEntity<Map<String, Object>> createAssignment(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/assignments", requestId, body, 201);
    }

    @PostMapping("/site-registry/assignments/{assignmentId}")
    public ResponseEntity<Map<String, Object>> updateAssignment(
            @PathVariable String assignmentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body
    ) {
        return proxyPost(indawoUrl + "/internal/v1/site-registry/assignments/" + assignmentId, requestId, body, 200);
    }

    @GetMapping("/site-registry/dashboard/summary")
    public ResponseEntity<Map<String, Object>> dashboardSummary(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(indawoUrl + "/internal/v1/site-registry/dashboard/summary", requestId);
    }

    // ── Helper ───────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> proxy(String url, String requestId) {
        try {
            JsonNode result = restTemplate.getForEntity(url, JsonNode.class).getBody();
            Object data = result != null && result.has("data") ? result.get("data") : (result != null ? result : new Object[0]);
            return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.warn("Public health proxy failed for {}: {}", url, e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }

    private ResponseEntity<Map<String, Object>> proxyPost(String url, String requestId, Map<String, Object> body, int expectedStatus) {
        try {
            JsonNode result = restTemplate.postForEntity(url, body, JsonNode.class).getBody();
            Object data = result != null && result.has("data") ? result.get("data") : (result != null ? result : Map.of());
            return ResponseEntity.status(expectedStatus).body(Map.of("data", data, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.warn("Public health proxy POST failed for {}: {}", url, e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "FAILED", "message", e.getMessage())));
        }
    }
}
