package zw.gov.mohcc.impilo.pacs.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Orthanc REST client for hierarchy sync, study lookup, and (future) ingest helpers.
 */
@Component
public class OrthancClient {

    private static final Logger log = LoggerFactory.getLogger(OrthancClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public OrthancClient(
            @Qualifier("orthancRestTemplate") RestTemplate restTemplate,
            @Value("${impilo.orthanc.base-url:http://localhost:8042}") String baseUrl,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUrl = trimmed;
        this.objectMapper = objectMapper;
    }

    public JsonNode getStudy(String orthancStudyId) {
        return getJson("/studies/" + orthancStudyId);
    }

    public JsonNode getSeries(String orthancSeriesId) {
        return getJson("/series/" + orthancSeriesId);
    }

    public JsonNode getInstanceSimplifiedTags(String orthancInstanceId) {
        return getJson("/instances/" + orthancInstanceId + "/simplified-tags");
    }

    /**
     * Resolves Orthanc internal study IDs for a DICOM Study Instance UID via {@code /tools/find}.
     *
     * @return Orthanc study resource ids (may be empty if unknown to this Orthanc)
     */
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
