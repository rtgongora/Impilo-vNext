package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.Optional;

/**
 * HTTP client for the Inpatient sovereign service (admissions, transfers, discharges).
 *
 * <p>Trust headers are forwarded by the RestTemplate interceptor in
 * {@link zw.gov.mohcc.impilo.experience.config.ServiceClientConfig}.</p>
 */
@Component
public class InpatientServiceClient {

    private static final Logger log = LoggerFactory.getLogger(InpatientServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public InpatientServiceClient(RestTemplate serviceRestTemplate,
                                  ServiceClientConfig.ServiceEndpoints endpoints,
                                  ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.inpatientBaseUrl();
        this.objectMapper = objectMapper;
    }

    public JsonNode createAdmission(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/admissions";
        log.info("INPATIENT: createAdmission operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getAdmission(String id) {
        String url = baseUrl + "/internal/v1/admissions/" + id;
        log.info("INPATIENT: getAdmission operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getActiveAdmissions(String subjectCpid, String facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/admissions/active")
                .queryParam("subject_cpid", subjectCpid)
                .queryParam("facility_id", facilityId)
                .encode()
                .toUriString();
        log.info("INPATIENT: getActiveAdmissions operation [subjectCpid={}, facilityId={}]",
                subjectCpid != null ? subjectCpid.substring(0, Math.min(8, subjectCpid.length())) + "..." : null,
                facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode dischargeAdmission(String id, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/admissions/" + id + "/discharge";
        log.info("INPATIENT: dischargeAdmission operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url, request == null || request.isEmpty() ? null : request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode transferPatient(String id, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/admissions/" + id + "/transfer";
        log.info("INPATIENT: transferPatient operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /**
     * Lists admissions. When {@code patientCpid} is set, filters using {@code subjectCpid} query
     * (forward-compatible; the sovereign admission list API currently supports
     * {@code facilityId} and {@code status} only).
     */
    public JsonNode listAdmissions(String patientCpid) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/admissions");
        Optional.ofNullable(patientCpid)
                .filter(s -> !s.isBlank())
                .ifPresent(cpid -> b.queryParam("subjectCpid", cpid));
        String url = b.encode().toUriString();
        log.info("INPATIENT: listAdmissions operation [patientCpid={}]",
                patientCpid != null ? patientCpid.substring(0, Math.min(8, patientCpid.length())) + "..." : null);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Lists ward rounds for an admission. The sovereign inpatient service does not yet expose this
     * on {@code AdmissionController}; the path follows the admissions resource namespace for future use.
     */
    public JsonNode listWardRounds(String admissionId) {
        String url = baseUrl + "/internal/v1/admissions/" + admissionId + "/ward-rounds";
        log.info("INPATIENT: listWardRounds operation [admissionId={}]", admissionId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Bed/Ward Management (strangler migration) ─────────────

    public JsonNode listWards(String facilityId) {
        String url = baseUrl + "/internal/v1/beds/wards?facility_id=" + facilityId;
        log.info("INPATIENT: listWards operation [facilityId={}]", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listBeds(String facilityId, String wardId, String status) {
        StringBuilder url = new StringBuilder(baseUrl + "/internal/v1/beds?facility_id=" + facilityId);
        if (wardId != null) url.append("&ward_id=").append(wardId);
        if (status != null) url.append("&status=").append(status);
        log.info("INPATIENT: listBeds operation [facilityId={}, wardId={}]", facilityId, wardId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateBedStatus(String bedId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/beds/" + bedId + "/status";
        log.info("INPATIENT: updateBedStatus operation [bedId={}]", bedId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode assignPatientToBed(String bedId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/beds/" + bedId + "/assign";
        log.info("INPATIENT: assignPatientToBed operation [bedId={}]", bedId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode dischargeBed(String bedId) {
        String url = baseUrl + "/internal/v1/beds/" + bedId + "/discharge";
        log.info("INPATIENT: dischargeBed operation [bedId={}]", bedId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
