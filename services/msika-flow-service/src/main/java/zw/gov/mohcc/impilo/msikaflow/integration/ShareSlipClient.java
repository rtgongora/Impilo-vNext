package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.msikaflow.core.UpstreamUnavailableException;

/**
 * Verifies Rx share-slip tokens against share-slip-service before attaching them to
 * an order. Replaces the previous hardcoded "validated and attached" stub — a token
 * is now only ever attached if share-slip-service confirms it is valid.
 */
@Service
public class ShareSlipClient {

    private static final Logger log = LoggerFactory.getLogger(ShareSlipClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public record ShareSlipVerification(boolean valid, String status, String subjectType) {}

    public ShareSlipClient(ObjectMapper objectMapper,
                           @Value("${msika-flow.integration.share-slip-url:http://localhost:8104}") String shareSlipBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(shareSlipBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * GET /v1/public/share/verify/{token}. Throws {@link UpstreamUnavailableException}
     * (mapped to HTTP 502) when share-slip-service cannot be reached — an unreachable
     * verifier is never treated as "valid".
     */
    public ShareSlipVerification verify(String token) {
        try {
            String body = restClient.get()
                    .uri("/v1/public/share/verify/{token}", token)
                    .retrieve()
                    .body(String.class);
            if (body == null) {
                throw new UpstreamUnavailableException("share-slip-service returned an empty response");
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            boolean valid = data.path("valid").asBoolean(false);
            String status = data.hasNonNull("status") ? data.get("status").asText() : null;
            String subjectType = data.hasNonNull("subjectType") ? data.get("subjectType").asText() : null;
            return new ShareSlipVerification(valid, status, subjectType);
        } catch (UpstreamUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("share-slip-service unreachable verifying token: {}", e.getMessage());
            throw new UpstreamUnavailableException("share-slip-service unreachable", e);
        } catch (Exception e) {
            log.error("share-slip-service verify response unparseable: {}", e.getMessage());
            throw new UpstreamUnavailableException("share-slip-service response unparseable", e);
        }
    }
}
