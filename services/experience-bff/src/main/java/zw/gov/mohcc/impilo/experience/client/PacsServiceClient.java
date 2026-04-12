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

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
