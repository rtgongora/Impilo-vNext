package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;

/**
 * Cookie-session authenticated bridge for VITO's citizen portal. Browser code
 * must never read or forward a bearer token; the BFF resolves the opaque session
 * and its server-side RestTemplate propagates the resulting actor context.
 */
@RestController
@RequestMapping("/internal/v1/vito/portal")
public class CitizenPortalBffController {

    private static final String STEP_UP_HEADER = "x-step-up-token";
    private final VitoServiceClient vito;

    public CitizenPortalBffController(VitoServiceClient vito) {
        this.vito = vito;
    }

    @PostMapping(value = "/id/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> requestId(@RequestBody Map<String, Object> body) {
        return forward(() -> vito.rawPost("/v1/portal/id/request", body));
    }

    @PostMapping(value = "/id/recovery/start", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> startRecovery(
            @RequestBody Map<String, Object> body,
            @RequestHeader(STEP_UP_HEADER) String stepUpToken) {
        return forward(() -> vito.rawPostWithHeader(
                "/v1/portal/id/recovery/start", body, STEP_UP_HEADER, stepUpToken));
    }

    @PostMapping(value = "/id/recovery/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> verifyRecovery(
            @RequestBody Map<String, Object> body,
            @RequestHeader(STEP_UP_HEADER) String stepUpToken) {
        return forward(() -> vito.rawPostWithHeader(
                "/v1/portal/id/recovery/verify", body, STEP_UP_HEADER, stepUpToken));
    }

    @GetMapping("/me")
    public ResponseEntity<String> me() {
        return forward(() -> vito.rawGet("/v1/portal/me"));
    }

    @GetMapping("/health-id/qr")
    public ResponseEntity<String> healthIdQr() {
        return forward(() -> vito.rawGet("/v1/portal/health-id/qr"));
    }

    @PostMapping(value = "/delegated-pickup/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createPickup(
            @RequestBody Map<String, Object> body,
            @RequestHeader(STEP_UP_HEADER) String stepUpToken) {
        return forward(() -> vito.rawPostWithHeader(
                "/v1/portal/delegated-pickup/create", body, STEP_UP_HEADER, stepUpToken));
    }

    @PostMapping(value = "/delegated-pickup/redeem", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> redeemPickup(@RequestBody Map<String, Object> body) {
        return forward(() -> vito.rawPost("/v1/portal/delegated-pickup/redeem", body));
    }

    @FunctionalInterface
    private interface UpstreamCall {
        ResponseEntity<String> call();
    }

    private static ResponseEntity<String> forward(UpstreamCall call) {
        try {
            ResponseEntity<String> upstream = call.call();
            MediaType contentType = upstream.getHeaders().getContentType();
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                    .body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            MediaType contentType = ex.getResponseHeaders() != null
                    ? ex.getResponseHeaders().getContentType() : null;
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }
}
