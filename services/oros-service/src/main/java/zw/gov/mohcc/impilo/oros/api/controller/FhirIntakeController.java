package zw.gov.mohcc.impilo.oros.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.core.FhirServiceRequestService;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;

/**
 * FHIR ServiceRequest-inbound adapter endpoint (external order intake).
 *
 * <p>Flag-gated by {@code oros.integration.fhir.servicerequest-inbound.enabled}: returns 501
 * NOT_CONFIGURED when off (honest NOT_LIVE seam, mirrored at {@code /admin/integrations}); when on,
 * maps the FHIR R4 ServiceRequest into an OROS order. Authenticated at the trust edge.</p>
 */
@RestController
@RequestMapping("/v1/fhir")
public class FhirIntakeController {

    private static final Logger log = LoggerFactory.getLogger(FhirIntakeController.class);

    private final FhirServiceRequestService fhirServiceRequestService;
    private final boolean enabled;

    public FhirIntakeController(
            FhirServiceRequestService fhirServiceRequestService,
            @Value("${oros.integration.fhir.servicerequest-inbound.enabled:false}") boolean enabled) {
        this.fhirServiceRequestService = fhirServiceRequestService;
        this.enabled = enabled;
    }

    @PostMapping("/ServiceRequest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> receiveServiceRequest(@RequestBody JsonNode serviceRequest) {
        String cid = TrustContextHolder.require().correlationId().toString();
        if (!enabled) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(ApiResponse.error(
                    "NOT_CONFIGURED", "FHIR ServiceRequest-inbound adapter is not enabled",
                    HttpStatus.NOT_IMPLEMENTED.value(), cid));
        }
        OrderEntity order = fhirServiceRequestService.ingest(serviceRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of(
                "orderId", order.getOrderId(),
                "status", order.getStatus().name(),
                "requestSource", order.getRequestSource().name()), cid));
    }
}
