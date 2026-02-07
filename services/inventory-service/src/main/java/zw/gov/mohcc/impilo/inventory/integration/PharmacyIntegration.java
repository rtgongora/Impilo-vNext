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
 * REST client for the pharmacy-service.
 *
 * <p>Notifies the pharmacy service when stock levels change for items
 * that may affect pharmacy operations (e.g. formulary availability,
 * dispensing constraints due to low stock).</p>
 *
 * <p>All external calls degrade gracefully: if the pharmacy service is
 * unavailable, the failure is logged and processing continues without
 * blocking the inventory workflow.</p>
 */
@Service
public class PharmacyIntegration {

    private static final Logger log = LoggerFactory.getLogger(PharmacyIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PharmacyIntegration(
            RestTemplate restTemplate,
            @Value("${inventory.integration.pharmacy-base-url:http://localhost:8096}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Notify the pharmacy service of a stock balance change.
     *
     * <p>Called when on-hand quantities change for pharmacy-relevant items.
     * The pharmacy service can use this to update its formulary availability
     * or trigger low-stock alerts for dispensing workflows.</p>
     *
     * @param facilityId the facility where the stock change occurred
     * @param itemCode   the item code whose balance changed
     * @param newBalance the new on-hand balance
     */
    public void notifyStockChange(UUID facilityId, String itemCode, int newBalance) {
        try {
            String url = baseUrl + "/v1/internal/stock-update";

            Map<String, Object> payload = new HashMap<>();
            payload.put("facilityId", facilityId.toString());
            payload.put("itemCode", itemCode);
            payload.put("newBalance", newBalance);
            payload.put("source", "inventory-service");

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(url, request, Void.class);

            log.info("Pharmacy stock change notified: facilityId={}, itemCode={}, newBalance={}",
                    facilityId, itemCode, newBalance);

        } catch (RestClientException e) {
            log.warn("Pharmacy service unavailable for stock change notification: " +
                     "facilityId={}, itemCode={}: {}", facilityId, itemCode, e.getMessage());
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
            log.debug("No trust context available for pharmacy call headers");
        }
        return headers;
    }
}
