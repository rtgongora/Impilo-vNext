package zw.gov.mohcc.impilo.madi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;

@Service
public class InventoryIntegration {

    private static final Logger log = LoggerFactory.getLogger(InventoryIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InventoryIntegration(RestTemplate restTemplate,
                                @Value("${madi.integration.inventory.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void notifyStockChange(String inventoryItemCode, String movementType, int quantity) {
        try {
            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = Map.of(
                    "inventoryItemCode", inventoryItemCode,
                    "movementType", movementType,
                    "quantity", quantity,
                    "source", "madi-service");
            restTemplate.postForEntity(baseUrl + "/internal/v1/inventory/stock-movements",
                    new HttpEntity<>(payload, headers), Void.class);
        } catch (RestClientException e) {
            log.warn("Inventory service unavailable for stock change {}: {}", inventoryItemCode, e.getMessage());
        }
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        } catch (IllegalStateException ignored) {
            // graceful in dev
        }
        return headers;
    }
}
