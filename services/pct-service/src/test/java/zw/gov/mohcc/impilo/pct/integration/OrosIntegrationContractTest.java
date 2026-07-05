package zw.gov.mohcc.impilo.pct.integration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CONTRACT-PINNING TEST — documents (does NOT fix) the known OROS {@code /v1/orders} payload
 * divergence tracked as serialized item R1 on the Fable parallel-delivery board.
 *
 * <p>pct-service posts {@code {journeyId, payload}} (this class), while inpatient-service
 * {@code OrosOrderClient.placeOrder} posts {@code {orderType, priority, patientCpid, encounterRef,
 * clinicalNotes, items[]}} and the OROS {@code PlaceOrderRequest} DTO requires {@code orderType}
 * (@NotNull) and {@code patientCpid} (@NotBlank). The PCT shape therefore fails bean validation
 * against a real OROS — orders degrade to FAILED provenance rows (honest, non-blocking).</p>
 *
 * <p>If this test starts failing because the wire shape changed, that change MUST have gone
 * through the coordinator-gated R1 contract unification. Do not silently update this pin.</p>
 */
class OrosIntegrationContractTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void submitOrder_pinsTheDivergentJourneyIdPayloadShape_R1() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(Map.of("orderId", "ord-9")));
        OrosIntegration integration = new OrosIntegration(restTemplate, "http://oros.test");

        Map<String, Object> result = integration.submitOrder("jny-1", "{\"code\":\"FBC\"}");
        assertThat(result).containsEntry("orderId", "ord-9");

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(restTemplate)
                .postForEntity(urlCaptor.capture(), bodyCaptor.capture(), eq(Map.class));

        assertThat(urlCaptor.getValue()).isEqualTo("http://oros.test/v1/orders");
        Map<String, Object> body = (Map<String, Object>) bodyCaptor.getValue();
        // R1: the PCT wire shape — NOT the OROS PlaceOrderRequest shape used by inpatient-service.
        assertThat(body.keySet()).containsExactlyInAnyOrder("journeyId", "payload");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void submitOrder_orosRejection_degradesToEmptyMapNotException() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("400 Bad Request"));
        OrosIntegration integration = new OrosIntegration(restTemplate, "http://oros.test");

        // A validation rejection from real OROS (the R1 mismatch symptom) must degrade
        // gracefully so the clinical journey is never blocked by ordering.
        assertThat(integration.submitOrder("jny-1", "{}")).isEmpty();
    }
}
