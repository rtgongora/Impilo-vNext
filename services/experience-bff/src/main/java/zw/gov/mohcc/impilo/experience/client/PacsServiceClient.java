package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.Optional;

/**
 * HTTP client for the PACS adapter sovereign service (imaging study registration and correlation).
 *
 * <p>Trust headers are forwarded by the RestTemplate interceptor in
 * {@link zw.gov.mohcc.impilo.experience.config.ServiceClientConfig}.</p>
 */
@Component
public class PacsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PacsServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public PacsServiceClient(RestTemplate serviceRestTemplate,
                             ServiceClientConfig.ServiceEndpoints endpoints,
                             ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.pacsBaseUrl();
        this.objectMapper = objectMapper;
    }

    /**
     * Lists imaging studies. Optional {@code patientCpid} is sent as {@code patientCpid} query
     * for forward compatibility (sovereign list endpoint is currently unfiltered).
     */
    public JsonNode listStudies(String patientCpid) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/imaging-studies");
        Optional.ofNullable(patientCpid)
                .filter(s -> !s.isBlank())
                .ifPresent(cpid -> b.queryParam("patientCpid", cpid));
        String url = b.encode().toUriString();
        log.info("PACS: listStudies operation [patientCpid={}]",
                patientCpid != null ? patientCpid.substring(0, Math.min(8, patientCpid.length())) + "..." : null);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getStudy(String studyId) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId;
        log.info("PACS: getStudy operation [studyId={}]", studyId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode correlateStudy(String studyId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/correlate";
        log.info("PACS: correlateStudy operation [studyId={}]", studyId);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PATCH, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Lists DICOM series for a study. The sovereign PACS adapter does not expose this yet; the path
     * is reserved for evolution alongside Orthanc integration.
     */
    public JsonNode listSeries(String studyId) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/series";
        log.info("PACS: listSeries operation [studyId={}]", studyId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listInstances(String studyId, String seriesId) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/series/" + seriesId + "/instances";
        log.info("PACS: listInstances operation [studyId={}, seriesId={}]", studyId, seriesId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode syncStudyHierarchy(String studyId) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/sync-hierarchy";
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode launchViewerSession(String studyId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/viewer-sessions";
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode viewerLaunchContext(String studyId, String viewerType) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/imaging-studies/" + studyId + "/viewer-launch-context");
        Optional.ofNullable(viewerType)
                .filter(s -> !s.isBlank())
                .ifPresent(v -> b.queryParam("viewerType", v));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.toUriString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode searchStudies(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/imaging-studies/search";
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode linkReport(String studyId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/report-links";
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listReportLinks(String studyId) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/report-links";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createAnnotation(String studyId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/annotations";
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listAnnotations(String studyId) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/annotations";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode opsStatus() {
        String url = baseUrl + "/internal/v1/imaging-studies/ops/status";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode opsUnmatchedStudies() {
        String url = baseUrl + "/internal/v1/imaging-studies/ops/unmatched-studies";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode opsFailedCorrelations() {
        String url = baseUrl + "/internal/v1/imaging-studies/ops/failed-correlations";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode opsFailedWritebacks() {
        String url = baseUrl + "/internal/v1/imaging-studies/ops/failed-writebacks";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode retryFailedWriteback(Long outboxId) {
        String url = baseUrl + "/internal/v1/imaging-studies/ops/failed-writebacks/" + outboxId + "/retry";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode retryAllFailedWritebacks() {
        String url = baseUrl + "/internal/v1/imaging-studies/ops/failed-writebacks/retry-all";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode opsExceptions(String status) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/imaging-studies/ops/exceptions");
        Optional.ofNullable(status)
                .filter(s -> !s.isBlank())
                .ifPresent(s -> b.queryParam("status", s));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.encode().toUriString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listStudyExceptions(String studyId) {
        String url = baseUrl + "/internal/v1/imaging-studies/" + studyId + "/exceptions";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode resolveException(Long exceptionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/imaging-studies/ops/exceptions/" + exceptionId + "/resolve";
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    // ── Facility imaging capability + modality registry ─────────────────────

    public JsonNode listFacilityCapabilities(String deploymentMode) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/imaging-facilities");
        Optional.ofNullable(deploymentMode)
                .filter(s -> !s.isBlank())
                .ifPresent(m -> b.queryParam("deploymentMode", m));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.encode().toUriString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFacilityCapability(String facilityId) {
        String url = baseUrl + "/internal/v1/imaging-facilities/" + facilityId + "/capability";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode upsertFacilityCapability(String facilityId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/imaging-facilities/" + facilityId + "/capability";
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode facilityImagingReadiness(String facilityId) {
        String url = baseUrl + "/internal/v1/imaging-facilities/" + facilityId + "/readiness";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listFacilityModalities(String facilityId, boolean includeInactive) {
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/imaging-facilities/" + facilityId + "/modalities")
                .queryParam("includeInactive", includeInactive)
                .encode().toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode registerFacilityModality(String facilityId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/imaging-facilities/" + facilityId + "/modalities";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateFacilityModality(String facilityId, Long modalityId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/imaging-facilities/" + facilityId + "/modalities/" + modalityId;
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode deactivateFacilityModality(String facilityId, Long modalityId) {
        String url = baseUrl + "/internal/v1/imaging-facilities/" + facilityId + "/modalities/" + modalityId + "/deactivate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
