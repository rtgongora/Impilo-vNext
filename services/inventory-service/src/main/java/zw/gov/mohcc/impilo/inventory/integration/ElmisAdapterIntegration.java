package zw.gov.mohcc.impilo.inventory.integration;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST client for the inventory-elmis-adapter service.
 *
 * <p>Provides integration with external eLMIS systems by pushing stock
 * movement events and fetching stock position snapshots. Used when the
 * facility is operating in ADAPTER or HYBRID capability mode.</p>
 *
 * <p>All external calls degrade gracefully: if the adapter is unavailable,
 * the failure is logged and processing continues without blocking the
 * inventory workflow.</p>
 */
@Service
public class ElmisAdapterIntegration {

    private static final Logger log = LoggerFactory.getLogger(ElmisAdapterIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ElmisAdapterIntegration(
            RestTemplate restTemplate,
            @Value("${inventory.integration.elmis-adapter-base-url:http://localhost:8099}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Push a stock movement event to the eLMIS adapter.
     *
     * <p>Called when a ledger event is recorded and the facility is operating
     * in ADAPTER or HYBRID mode. The adapter translates the event into the
     * format required by the external eLMIS system.</p>
     *
     * @param event a map containing the stock movement details (itemCode, batch,
     *              qty, eventType, facilityId, storeId, etc.)
     */
    public void pushStockMovement(Map<String, Object> event) {
        try {
            String url = baseUrl + "/v1/stock-movements";

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

            restTemplate.postForEntity(url, request, Void.class);

            log.info("Stock movement pushed to eLMIS adapter: itemCode={}, eventType={}",
                    event.get("itemCode"), event.get("eventType"));

        } catch (RestClientException e) {
            log.warn("eLMIS adapter unavailable for stock movement push: {}", e.getMessage());
        }
    }

    /**
     * Fetch the current stock position from the external eLMIS for a specific item.
     *
     * <p>Returns the external system's view of the stock balance. Used for
     * on-demand reconciliation checks.</p>
     *
     * @param facilityId the facility identifier
     * @param itemCode   the item code to query
     * @return a map containing the external stock position, or null if unavailable
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchStockPosition(UUID facilityId, String itemCode) {
        try {
            String url = baseUrl + "/v1/stock-positions?facilityId=" + facilityId + "&itemCode=" + itemCode;

            HttpHeaders headers = buildTrustHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            var response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Stock position fetched from eLMIS: facilityId={}, itemCode={}",
                        facilityId, itemCode);
                return response.getBody();
            }

            return null;

        } catch (RestClientException e) {
            log.warn("eLMIS adapter unavailable for stock position fetch: facilityId={}, itemCode={}: {}",
                    facilityId, itemCode, e.getMessage());
            return null;
        }
    }

    /**
     * Build HTTP headers with trust context for inter-service communication.
     */
    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) {
                headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            }
        } catch (IllegalStateException e) {
            log.debug("No trust context available for eLMIS adapter call headers");
        }
        return headers;
    }
}
