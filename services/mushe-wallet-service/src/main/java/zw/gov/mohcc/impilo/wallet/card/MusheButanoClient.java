package zw.gov.mohcc.impilo.wallet.card;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Fetches the real patient summary from BUTANO so the card's PHR carries actual
 * clinical data instead of the {@code PENDING_SYNC} placeholder. Called from the
 * Kafka encounter consumer and the manual sync path — the consumer has no inbound
 * HTTP request, so v1.1 trust headers are SYNTHESIZED (tenant + request/correlation
 * ids), the {@code service-to-service-v11-headers} pattern.
 *
 * <p>Fail-open-for-sync: on any error it returns {@code null}, and the caller keeps
 * the {@code PENDING_SYNC} marker — the card never gets garbage, and a BUTANO
 * outage doesn't corrupt the stored summary or block the encounter.</p>
 */
@Component
public class MusheButanoClient {

    private static final Logger log = LoggerFactory.getLogger(MusheButanoClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String butanoBaseUrl;

    public MusheButanoClient(@Value("${BUTANO_BASE_URL:${mushe.butano.base-url:http://localhost:8090}}") String butanoBaseUrl) {
        this.butanoBaseUrl = butanoBaseUrl.endsWith("/") ? butanoBaseUrl.substring(0, butanoBaseUrl.length() - 1) : butanoBaseUrl;
    }

    /**
     * Fetch the real IPS (International Patient Summary) FHIR Bundle for a CPID.
     * @return the bundle JSON, or {@code null} if unavailable (caller keeps PENDING_SYNC).
     */
    public String fetchIpsSummary(String cpid, UUID tenantId) {
        if (cpid == null || cpid.isBlank()) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (tenantId != null) {
                headers.set("X-Tenant-ID", tenantId.toString());
            }
            headers.set("X-Pod-ID", "national");
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Correlation-ID", UUID.randomUUID().toString());
            var resp = restTemplate.exchange(
                    butanoBaseUrl + "/v1/summary/ips/" + cpid,
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            String body = resp.getBody();
            return (body == null || body.isBlank()) ? null : body;
        } catch (RuntimeException e) {
            log.warn("BUTANO IPS fetch failed for cpid (keeping PENDING_SYNC): {}", e.getMessage());
            return null;
        }
    }
}
