package zw.gov.mohcc.impilo.inpatient.integration;

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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client to inventory-service (Dura, 8098) for recording ward stock CONSUMPTION when a medication
 * is administered via the eMAR. Inpatient does NOT own stock — it records consumption against the ward
 * store via {@code POST /v1/internal/consumption/clinical} (refType=INPATIENT_EMAR). Best-effort: an
 * inventory outage must never block medication administration (the clinical record is the SoR for the
 * fact of administration; the stock decrement is a downstream ledger event).
 */
@Service
public class InventoryConsumptionClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryConsumptionClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;

    public InventoryConsumptionClient(RestTemplate inpatientRestTemplate,
                                      @Value("${inpatient.integration.inventory.base-url:http://localhost:8098}") String baseUrl,
                                      @Value("${inpatient.integration.inventory.enabled:true}") boolean enabled) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    /**
     * Record clinical consumption of a medication against a ward store.
     *
     * @return true if the call succeeded; false if skipped or the inventory service was unavailable.
     */
    public boolean recordEmarConsumption(String storeId, String itemCode, int qty, String orderId) {
        if (!enabled) {
            return false;
        }
        if (storeId == null || itemCode == null || qty <= 0) {
            log.debug("INPATIENT: skipping inventory consumption — incomplete stock context "
                    + "(store={}, item={}, qty={})", storeId, itemCode, qty);
            return false;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            TrustContext ctx = TrustContextHolder.require();
            payload.put("facilityId", ctx.facilityId() != null ? ctx.facilityId().toString() : null);
            payload.put("storeId", storeId);
            payload.put("itemCode", itemCode);
            payload.put("qty", qty);
            payload.put("refType", "INPATIENT_EMAR");
            payload.put("orderId", orderId);
            restTemplate.postForEntity(baseUrl + "/v1/internal/consumption/clinical",
                    new HttpEntity<>(payload, trustHeaders()), Void.class);
            log.info("INPATIENT: recorded eMAR consumption item={} qty={} store={} order={}",
                    itemCode, qty, storeId, orderId);
            return true;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT: inventory consumption unavailable for item {} (best-effort): {}",
                    itemCode, e.getMessage());
            return false;
        }
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            if (ctx.correlationId() != null) headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
        } catch (IllegalStateException ignored) {
            // no trust context (e.g. async): send unauthenticated, inventory will reject — logged above
        }
        return headers;
    }
}
