package zw.gov.mohcc.impilo.pharmacy.integration;

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

/**
 * Integration service for the OROS Orders and Results Orchestration service.
 *
 * <p>Pushes dispense status updates and results back to OROS when the pharmacy
 * workflow reaches milestones. This keeps the OROS order state synchronized
 * with the pharmacy fulfillment state.</p>
 *
 * <p>All external calls degrade gracefully: if OROS is unavailable, the
 * failure is logged and processing continues without blocking the
 * pharmacy workflow.</p>
 */
@Service
public class OrosIntegration {

    private static final Logger log = LoggerFactory.getLogger(OrosIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrosIntegration(RestTemplate restTemplate,
                           @Value("${pharmacy.integration.oros.base-url:http://localhost:8089}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Push a dispense status update to OROS.
     *
     * <p>Notifies OROS that the pharmacy dispense order has reached a new state.
     * OROS uses this to update the parent clinical order's status accordingly.</p>
     *
     * @param orosOrderId the OROS order ID linked to this dispense
     * @param status      the new dispense status
     * @param summary     a summary description of the status change
     */
    public void pushDispenseStatus(String orosOrderId, String status, String summary) {
        try {
            String url = baseUrl + "/v1/internal/pharmacy/status";

            Map<String, Object> payload = new HashMap<>();
            payload.put("orosOrderId", orosOrderId);
            payload.put("status", status);
            payload.put("summary", summary);
            payload.put("source", "pharmacy-service");

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(url, request, Void.class);

            log.info("OROS status update pushed: orosOrderId={}, status={}",
                    orosOrderId, status);

        } catch (RestClientException e) {
            log.warn("OROS unavailable for status update, orosOrderId={}: {}",
                    orosOrderId, e.getMessage());
        }
    }

    /**
     * Push a dispense result to OROS for PHARMACY order types.
     *
     * <p>Sends the dispense result JSON so OROS can record the outcome
     * and trigger any downstream workflows (e.g. billing, clinical record).</p>
     *
     * @param orosOrderId the OROS order ID
     * @param resultJson  the JSON result payload
     */
    public void pushDispenseResult(String orosOrderId, String resultJson) {
        try {
            String url = baseUrl + "/v1/internal/pharmacy/result";

            Map<String, Object> payload = new HashMap<>();
            payload.put("orosOrderId", orosOrderId);
            payload.put("result", resultJson);
            payload.put("source", "pharmacy-service");

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(url, request, Void.class);

            log.info("OROS dispense result pushed: orosOrderId={}", orosOrderId);

        } catch (RestClientException e) {
            log.warn("OROS unavailable for result push, orosOrderId={}: {}",
                    orosOrderId, e.getMessage());
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
            log.debug("No trust context available for OROS call headers");
        }
        return headers;
    }
}
