package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ZiboServiceClient;

import java.util.Map;

/**
 * Read-through to ZIBO for registry terminology (CodeSystem / ValueSet artifacts by canonical URL).
 */
@RestController
@RequestMapping("/internal/v1/registry/zibo")
public class ZiboRegistryProxyController {

    private static final Logger log = LoggerFactory.getLogger(ZiboRegistryProxyController.class);

    private final ZiboServiceClient ziboClient;

    public ZiboRegistryProxyController(ZiboServiceClient ziboClient) {
        this.ziboClient = ziboClient;
    }

    @GetMapping("/artifacts/resolve")
    public ResponseEntity<Map<String, Object>> resolve(
            @RequestParam String canonicalUrl,
            @RequestParam(required = false) String version,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = ziboClient.resolveArtifactByCanonical(canonicalUrl, version);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // An empty 200 here is indistinguishable from a successful read that found
            // nothing, so a failed call was being reported as an absence.
            log.error("ZIBO resolve failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "zibo_resolve_unavailable",
                    "message", "The artifact could not be resolved. Do not treat this as a missing artifact.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
