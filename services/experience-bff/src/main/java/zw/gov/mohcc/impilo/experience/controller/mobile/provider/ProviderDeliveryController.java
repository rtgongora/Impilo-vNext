package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DispatchServiceClient;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/mobile/provider/deliveries")
public class ProviderDeliveryController {

    private final DispatchServiceClient dispatchClient;

    public ProviderDeliveryController(DispatchServiceClient dispatchClient) {
        this.dispatchClient = dispatchClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> assigned(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = dispatchClient.listDeliveries();
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody(required = false) Map<String, Object> body) {
        return action(id, "accept", requestId, correlationId, body);
    }
    @PostMapping("/{id}/decline")
    public ResponseEntity<Map<String, Object>> decline(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody(required = false) Map<String, Object> body) {
        return action(id, "decline", requestId, correlationId, body);
    }
    @PostMapping("/{id}/pickup")
    public ResponseEntity<Map<String, Object>> pickup(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody(required = false) Map<String, Object> body) {
        return action(id, "pickup", requestId, correlationId, body);
    }
    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody(required = false) Map<String, Object> body) {
        return action(id, "start", requestId, correlationId, body);
    }
    @PostMapping("/{id}/location")
    public ResponseEntity<Map<String, Object>> location(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody(required = false) Map<String, Object> body) {
        return action(id, "location", requestId, correlationId, body);
    }
    @PostMapping("/{id}/proof")
    public ResponseEntity<Map<String, Object>> proof(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody(required = false) Map<String, Object> body) {
        return action(id, "proof", requestId, correlationId, body);
    }
    @PostMapping("/{id}/fail")
    public ResponseEntity<Map<String, Object>> fail(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody(required = false) Map<String, Object> body) {
        return action(id, "fail", requestId, correlationId, body);
    }
    @PostMapping("/{id}/return")
    public ResponseEntity<Map<String, Object>> returnDelivery(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId, @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId, @RequestBody(required = false) Map<String, Object> body) {
        return action(id, "return", requestId, correlationId, body);
    }

    private ResponseEntity<Map<String, Object>> action(String id, String action, String requestId, String correlationId, Map<String, Object> body) {
        JsonNode data = dispatchClient.deliveryAction(id, action, body != null ? body : Map.of());
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
