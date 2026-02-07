package zw.gov.mohcc.impilo.oros.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.WorkstepEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Integration service for the PCT Patient Care Tracker.
 *
 * <p>Notifies PCT about expected worksteps when an order is placed, and
 * about available results when they are captured. This allows PCT to
 * update task boards and trigger downstream workflows.</p>
 *
 * <p>All external calls degrade gracefully: if PCT is unavailable, the
 * failure is logged and processing continues normally.</p>
 */
@Service
public class PctIntegration {

    private static final Logger log = LoggerFactory.getLogger(PctIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PctIntegration(RestTemplate restTemplate,
                          @Value("${oros.integration.pct.base-url:http://localhost:8088}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Notify PCT about expected worksteps for an order.
     *
     * <p>Sends the list of workstep types so PCT can create corresponding
     * task entries for the patient's journey.</p>
     *
     * @param order the order entity
     * @param steps the worksteps created for the order
     */
    public void notifyExpectedWorksteps(OrderEntity order, List<WorkstepEntity> steps) {
        try {
            String url = baseUrl + "/v1/internal/oros/worksteps";

            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", order.getOrderId());
            payload.put("orderType", order.getOrderType().name());
            payload.put("patientCpid", order.getPatientCpid());
            payload.put("encounterRef", order.getEncounterRef());
            payload.put("facilityId", order.getFacilityId().toString());

            List<Map<String, String>> stepDtos = steps.stream()
                    .map(s -> Map.of(
                            "workstepId", s.getWorkstepId().toString(),
                            "stepType", s.getStepType().name(),
                            "status", s.getStatus().name()))
                    .collect(Collectors.toList());
            payload.put("worksteps", stepDtos);

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(url, request, Void.class);

            log.info("PCT notified of {} worksteps for order {}",
                    steps.size(), order.getOrderId());

        } catch (RestClientException e) {
            log.warn("PCT unavailable for workstep notification, orderId={}: {}",
                    order.getOrderId(), e.getMessage());
        }
    }

    /**
     * Notify PCT that a result is available for an order.
     *
     * @param order  the order entity
     * @param result the result entity
     */
    public void notifyResultAvailable(OrderEntity order, ResultEntity result) {
        try {
            String url = baseUrl + "/v1/internal/oros/result";

            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", order.getOrderId());
            payload.put("orderType", order.getOrderType().name());
            payload.put("patientCpid", order.getPatientCpid());
            payload.put("encounterRef", order.getEncounterRef());
            payload.put("facilityId", order.getFacilityId().toString());
            payload.put("resultId", result.getResultId().toString());
            payload.put("resultKind", result.getKind().name());
            payload.put("isCritical", result.isCritical());

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(url, request, Void.class);

            log.info("PCT notified of result for order {}, resultId={}, critical={}",
                    order.getOrderId(), result.getResultId(), result.isCritical());

        } catch (RestClientException e) {
            log.warn("PCT unavailable for result notification, orderId={}: {}",
                    order.getOrderId(), e.getMessage());
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
            log.debug("No trust context available for PCT call headers");
        }
        return headers;
    }
}
