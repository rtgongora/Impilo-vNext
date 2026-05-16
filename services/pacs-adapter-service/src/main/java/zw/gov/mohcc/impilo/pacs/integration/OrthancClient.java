package zw.gov.mohcc.impilo.pacs.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Orthanc REST client for hierarchy sync, study lookup, and (future) ingest helpers.
 */
@Component
@ConditionalOnProperty(name = "impilo.pacs.backend.provider", havingValue = "ORTHANC", matchIfMissing = true)
public class OrthancClient implements PacsBackendClient {

    private static final Logger log = LoggerFactory.getLogger(OrthancClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String backendMode;
    private final ObjectMapper objectMapper;

    public OrthancClient(
            @Qualifier("orthancRestTemplate") RestTemplate restTemplate,
            @Value("${impilo.orthanc.base-url:http://localhost:8042}") String baseUrl,
            @Value("${impilo.pacs.backend.mode:EMBEDDED}") String backendMode,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUrl = trimmed;
        this.backendMode = backendMode;
        this.objectMapper = objectMapper;
    }

    @Override
    public String backendProvider() {
        return "ORTHANC";
    }

    @Override
    public String backendMode() {
        return backendMode;
    }

    @Override
    public Map<String, Object> capabilities() {
        return Map.of(
                "provider", backendProvider(),
                "mode", backendMode(),
                "studyQuery", true,
                "studyRetrieve", true,
                "dicomweb", true,
                "dimse", false,
                "hierarchySync", true
        );
    }

    @Override
    public JsonNode getStudy(String orthancStudyId) {
        return getJson("/studies/" + orthancStudyId);
    }

    @Override
    public JsonNode getSeries(String orthancSeriesId) {
        return getJson("/series/" + orthancSeriesId);
    }

    @Override
    public JsonNode getInstanceSimplifiedTags(String orthancInstanceId) {
        return getJson("/instances/" + orthancInstanceId + "/simplified-tags");
    }

    /**
     * Lightweight connectivity probe used by PACS adapter ops status endpoints.
     */
    @Override
    public Map<String, Object> systemStatus() {
        try {
            JsonNode system = getJson("/system");
            return Map.of(
                    "reachable", true,
                    "provider", backendProvider(),
                    "mode", backendMode(),
                    "baseUrl", baseUrl,
                    "capabilities", capabilities(),
                    "system", system);
        } catch (RestClientException e) {
            return Map.of(
                    "reachable", false,
                    "provider", backendProvider(),
                    "mode", backendMode(),
                    "baseUrl", baseUrl,
                    "capabilities", capabilities(),
                    "error", e.getMessage() != null ? e.getMessage() : "Orthanc connectivity failed");
        }
    }

    /**
     * Resolves Orthanc internal study IDs for a DICOM Study Instance UID via {@code /tools/find}.
     *
     * @return Orthanc study resource ids (may be empty if unknown to this Orthanc)
     */
    @Override
    public List<String> findStudyIdsByStudyInstanceUid(String studyInstanceUid) {
        if (studyInstanceUid == null || studyInstanceUid.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("Level", "Study");
            ObjectNode query = objectMapper.createObjectNode();
            query.put("StudyInstanceUID", studyInstanceUid);
            body.set("Query", query);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            var response = restTemplate.exchange(
                    baseUrl + "/tools/find",
                    HttpMethod.POST,
                    new HttpEntity<>(body.toString(), headers),
                    JsonNode.class);
            JsonNode root = response.getBody();
            if (root == null || !root.isArray()) {
                return Collections.emptyList();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode n : root) {
                if (n.isTextual()) {
                    out.add(n.asText());
                }
            }
            return out;
        } catch (RestClientException e) {
            log.warn("Orthanc /tools/find failed for StudyInstanceUID: {}", studyInstanceUid, e);
            return Collections.emptyList();
        }
    }

    private JsonNode getJson(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try {
            var response = restTemplate.exchange(
                    baseUrl + path,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class);
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("Orthanc request failed: {} — {}", path, e.getMessage());
            throw e;
        }
    }
}
