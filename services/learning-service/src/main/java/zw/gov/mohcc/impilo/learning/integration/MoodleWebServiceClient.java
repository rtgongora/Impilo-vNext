package zw.gov.mohcc.impilo.learning.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.learning.config.LearningProperties;

/**
 * Minimal Moodle REST client (token + {@code webservice/rest/server.php}). Used for diagnostics
 * and optional completion pulls; tokens and capabilities are environment-specific.
 */
@Component
public class MoodleWebServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MoodleWebServiceClient.class);

    private final RestTemplate restTemplate;
    private final LearningProperties learningProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MoodleWebServiceClient(RestTemplate learningRestTemplate, LearningProperties learningProperties) {
        this.restTemplate = learningRestTemplate;
        this.learningProperties = learningProperties;
    }

    public boolean isConfigured() {
        String t = learningProperties.getMoodle().getWsToken();
        return t != null && !t.isBlank();
    }

    public Optional<JsonNode> call(String wsfunction, Map<String, String> formFields) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String endpoint = resolveEndpoint();
        UriComponentsBuilder b =
                UriComponentsBuilder.fromUriString(endpoint)
                        .queryParam("wstoken", learningProperties.getMoodle().getWsToken())
                        .queryParam("wsfunction", wsfunction)
                        .queryParam("moodlewsrestformat", "json");
        if (formFields != null) {
            for (Map.Entry<String, String> e : formFields.entrySet()) {
                if (e.getValue() != null) {
                    b.queryParam(e.getKey(), e.getValue());
                }
            }
        }
        try {
            String raw = restTemplate.getForObject(b.toUriString(), String.class);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(raw));
        } catch (Exception e) {
            log.warn("Moodle WS {} failed: {}", wsfunction, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonNode> getSiteInfo() {
        return call("core_webservice_get_site_info", Map.of());
    }

    public Optional<JsonNode> getActivitiesCompletionStatus(String courseId, String moodleUserId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("courseid", courseId);
        m.put("userid", moodleUserId);
        return call("core_completion_get_activities_completion_status", m);
    }

    private String resolveEndpoint() {
        String explicit = learningProperties.getMoodle().getWsEndpointUrl();
        if (explicit != null && !explicit.isBlank()) {
            return explicit.endsWith("/") ? explicit.substring(0, explicit.length() - 1) : explicit;
        }
        String base = learningProperties.getFundo().getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/webservice/rest/server.php";
    }
}
