package zw.gov.mohcc.impilo.tshepo.authz.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class LegacyPolicyCompatibilityProxyService {

    private final RestTemplate restTemplate;
    private final String legacyBaseUrl;

    public LegacyPolicyCompatibilityProxyService(
            RestTemplate restTemplate,
            @Value("${tshepo.authz.legacy-policy.base-url:http://localhost:8079}") String legacyBaseUrl) {
        this.restTemplate = restTemplate;
        this.legacyBaseUrl = legacyBaseUrl != null && legacyBaseUrl.endsWith("/")
                ? legacyBaseUrl.substring(0, legacyBaseUrl.length() - 1)
                : legacyBaseUrl;
    }

    public ResponseEntity<Map> forwardPost(String path, Map<String, Object> body, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        copyIfPresent(headers, request, "Authorization");
        copyIfPresent(headers, request, "X-Tenant-Id");
        copyIfPresent(headers, request, "X-Correlation-Id");
        copyIfPresent(headers, request, "X-Actor-Id");
        copyIfPresent(headers, request, "X-Actor-Type");
        copyIfPresent(headers, request, "X-Purpose-Of-Use");
        copyIfPresent(headers, request, "X-Facility-Id");
        copyIfPresent(headers, request, "X-Workspace-Id");
        copyIfPresent(headers, request, "X-Shift-Id");
        copyIfPresent(headers, request, "X-Device-Fingerprint");

        try {
            return restTemplate.exchange(
                    legacyBaseUrl + path,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Legacy policy dependency unavailable",
                    ex);
        }
    }

    private static void copyIfPresent(HttpHeaders headers, HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }
}
