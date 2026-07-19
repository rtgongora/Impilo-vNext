package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Thin BFF client for the ABIS biometric SoR (extract / enrol / verify). Uses the
 * trust-header-forwarding {@code serviceRestTemplate}, so the inbound X-Tenant-ID
 * and actor context flow through to ABIS. Reads {@code ABIS_BASE_URL} directly
 * (independent of the large ServiceEndpoints record).
 */
@Component
public class AbisBiometricClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AbisBiometricClient(@Qualifier("serviceRestTemplate") RestTemplate serviceRestTemplate,
                               @Value("${ABIS_BASE_URL:http://localhost:8186}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Extract a template from a capture image. */
    public JsonNode extract(Map<String, Object> body) {
        return post("/v1/abis/templates:extract", body);
    }

    /** Enrol an (already-extracted) template under an opaque subject_ref. */
    public JsonNode enroll(Map<String, Object> body) {
        return post("/v1/abis/templates", body);
    }

    /** 1:1 verify a probe template against a subject's enrolled template. */
    public JsonNode verify(Map<String, Object> body) {
        return post("/v1/abis/verify", body);
    }

    private JsonNode post(String path, Map<String, Object> body) {
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(baseUrl + path, body, JsonNode.class);
        return resp.getBody();
    }
}
