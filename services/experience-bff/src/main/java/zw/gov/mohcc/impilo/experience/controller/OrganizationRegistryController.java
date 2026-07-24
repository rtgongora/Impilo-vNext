package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BFF proxy for the Organization Registry service. Fail-closed: any downstream
 * failure returns HTTP 502 (no stub fallback) — organization onboarding and
 * Channel-C claims are governance actions that must never be served from stubs.
 */
@RestController
@RequestMapping("/internal/v1/organizations")
public class OrganizationRegistryController {

    private static final Logger log = LoggerFactory.getLogger(OrganizationRegistryController.class);

    private final OrganizationRegistryServiceClient client;
    private final DocumentServiceClient documents;

    public OrganizationRegistryController(OrganizationRegistryServiceClient client,
                                          DocumentServiceClient documents) {
        this.client = client;
        this.documents = documents;
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadLogo(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id,
            @RequestPart("file") MultipartFile file) {
        try {
            if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) {
                return invalidLogo("Logo must be a non-empty image no larger than 2 MB",
                        requestId, correlationId);
            }
            String mime = file.getContentType();
            if (!java.util.Set.of("image/png", "image/jpeg", "image/webp").contains(mime)) {
                return invalidLogo("Logo must be PNG, JPEG, or WebP", requestId, correlationId);
            }
            JsonNode stored = documents.uploadObject(
                    file.getBytes(), file.getOriginalFilename(), mime,
                    Map.of("purpose", "organization-logo", "organizationId", id));
            String objectId = objectId(stored);
            if (objectId == null) {
                throw new IllegalStateException("document-service did not return an object id");
            }
            return ok(requestId, correlationId,
                    client.updateLogo(id, Map.of("logoObjectId", objectId)));
        } catch (IOException | RuntimeException e) {
            log.warn("organization logo upload failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String status) {
        try {
            return ok(requestId, correlationId, client.listOrganizations(status));
        } catch (Exception e) {
            log.warn("list organizations failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id) {
        try {
            return ok(requestId, correlationId, client.getOrganization(id));
        } catch (Exception e) {
            log.warn("get organization failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return created(requestId, correlationId, client.createOrganization(body));
        } catch (Exception e) {
            log.warn("create organization failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/{id}/representatives")
    public ResponseEntity<Map<String, Object>> listRepresentatives(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id) {
        try {
            return ok(requestId, correlationId, client.listRepresentatives(id));
        } catch (Exception e) {
            log.warn("list representatives failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/representatives")
    public ResponseEntity<Map<String, Object>> addRepresentative(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            return created(requestId, correlationId, client.addRepresentative(id, body));
        } catch (Exception e) {
            log.warn("add representative failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/submit-verification")
    public ResponseEntity<Map<String, Object>> submitVerification(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id) {
        try {
            return ok(requestId, correlationId, client.submitVerification(id));
        } catch (Exception e) {
            log.warn("submit verification failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            return ok(requestId, correlationId, client.verify(id, body));
        } catch (Exception e) {
            log.warn("verify organization failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/{id}/affiliations")
    public ResponseEntity<Map<String, Object>> listAffiliations(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id) {
        try {
            return ok(requestId, correlationId, client.listAffiliations(id));
        } catch (Exception e) {
            log.warn("list affiliations failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/affiliations")
    public ResponseEntity<Map<String, Object>> createAffiliation(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            return created(requestId, correlationId, client.createAffiliation(id, body));
        } catch (Exception e) {
            log.warn("create affiliation failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/claims")
    public ResponseEntity<Map<String, Object>> submitClaim(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            return created(requestId, correlationId, client.submitClaim(id, body));
        } catch (Exception e) {
            log.warn("submit claim failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/claims/{claimId}")
    public ResponseEntity<Map<String, Object>> getClaim(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String claimId) {
        try {
            return ok(requestId, correlationId, client.getClaim(claimId));
        } catch (Exception e) {
            log.warn("get claim failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, JsonNode data) {
        return ResponseEntity.ok(envelope(requestId, correlationId, data));
    }

    private static ResponseEntity<Map<String, Object>> created(String requestId, String correlationId, JsonNode data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(envelope(requestId, correlationId, data));
    }

    private static Map<String, Object> envelope(String requestId, String correlationId, JsonNode data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("requestId", requestId);
        body.put("correlationId", correlationId);
        body.put("data", data);
        return body;
    }

    private static ResponseEntity<Map<String, Object>> upstreamFailure(
            String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", "ORGANIZATION_REGISTRY_UNAVAILABLE",
                        "message", message == null ? "organization-registry unavailable" : message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static ResponseEntity<Map<String, Object>> invalidLogo(
            String message, String requestId, String correlationId) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", Map.of("code", "INVALID_ORGANIZATION_LOGO", "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static String objectId(JsonNode stored) {
        if (stored == null) return null;
        if (stored.hasNonNull("objectId")) return stored.get("objectId").asText();
        if (stored.hasNonNull("id")) return stored.get("id").asText();
        return null;
    }
}
