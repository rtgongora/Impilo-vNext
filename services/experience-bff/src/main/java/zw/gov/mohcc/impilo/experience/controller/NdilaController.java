package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NdilaServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/ndila")
public class NdilaController {

    private static final Logger log = LoggerFactory.getLogger(NdilaController.class);
    private final NdilaServiceClient client;

    public NdilaController(NdilaServiceClient client) {
        this.client = client;
    }

    @GetMapping("/tiles/config")
    public ResponseEntity<Map<String, Object>> tileConfig(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.tileConfig();
            if (data == null || data.isNull()) {
                return unavailable("NDILA_EMPTY_CONFIG", "Ndila returned no tile configuration", requestId, correlationId);
            }
            // The ndila service advertises tile templates under its own /api/v1/... namespace,
            // but the browser can only reach tiles through this BFF at /internal/v1/ndila/tiles/...
            // (which is also the prefix the web client's trust-header synthesis matches). Rewrite
            // the templates so tiles actually load in the deployed shell (QA #1).
            rewriteTileTemplates(data);
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila tile config failed: {}", e.getMessage());
            return unavailable("NDILA_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/tiles/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> tileRaster(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        try {
            byte[] png = client.tilePng(z, x, y);
            if (png.length == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                    .contentType(MediaType.IMAGE_PNG)
                    .body(png);
        } catch (Exception e) {
            log.warn("Ndila tile raster failed z={} x={} y={}: {}", z, x, y, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/tiles/{z}/{x}/{y}.mvt")
    public ResponseEntity<byte[]> tileVector(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        try {
            byte[] tile = client.tileVector(z, x, y);
            if (tile.length == 0) {
                return ResponseEntity.noContent().build();
            }
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                    .contentType(MediaType.parseMediaType("application/x-protobuf"));
            // Martin stores tiles gzipped; forward the encoding so the browser inflates.
            if (tile.length >= 2 && (tile[0] & 0xFF) == 0x1F && (tile[1] & 0xFF) == 0x8B) {
                builder.header(HttpHeaders.CONTENT_ENCODING, "gzip");
            }
            return builder.body(tile);
        } catch (Exception e) {
            log.warn("Ndila tile vector failed z={} x={} y={}: {}", z, x, y, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @PostMapping("/geocode")
    public ResponseEntity<Map<String, Object>> geocode(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.geocode(body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila geocode failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", Map.of("success", false, "denialReason", e.getMessage()),
                    "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @PostMapping("/reverse-geocode")
    public ResponseEntity<Map<String, Object>> reverseGeocode(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.reverseGeocode(body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila reverse-geocode failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", Map.of("success", false, "denialReason", e.getMessage()),
                    "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @PostMapping("/spatial/nearby")
    public ResponseEntity<Map<String, Object>> spatialNearby(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.spatialNearby(body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila spatial nearby failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", Map.of(),
                    "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @PostMapping("/spatial/nearest")
    public ResponseEntity<Map<String, Object>> spatialNearest(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.spatialNearest(body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila spatial nearest failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", Map.of(),
                    "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @GetMapping("/facilities/geocode-review-queue")
    public ResponseEntity<Map<String, Object>> geocodeReviewQueue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.geocodeReviewQueue();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila geocode review queue failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", List.of(),
                    "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    // ── Catchment resolution (R6/G4) — ndila is the geography SoR ─────────────

    /** Which catchments contain a point — makes facility↔catchment resolution reachable. */
    @PostMapping("/catchments/check-point")
    public ResponseEntity<Map<String, Object>> catchmentCheckPoint(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.catchmentCheckPoint(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : List.of(), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila catchment check-point failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", List.of(), "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @PostMapping("/catchments/nearest-service")
    public ResponseEntity<Map<String, Object>> catchmentNearestService(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.catchmentNearestService(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila catchment nearest-service failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", Map.of(), "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @PostMapping("/catchments/coverage-summary")
    public ResponseEntity<Map<String, Object>> catchmentCoverageSummary(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.catchmentCoverageSummary(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila catchment coverage-summary failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", Map.of(), "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @GetMapping("/catchments/by-owner/{ownerService}/{ownerEntityId}")
    public ResponseEntity<Map<String, Object>> catchmentsByOwner(
            @PathVariable String ownerService,
            @PathVariable String ownerEntityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.catchmentsByOwner(ownerService, ownerEntityId);
            return ResponseEntity.ok(Map.of("data", data != null ? data : List.of(), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila catchments by-owner failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", List.of(), "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    /** Canonical Ndila locations owned by a domain entity (G4 — e.g. ownerService=vito, ownerEntityId=healthId). */
    @GetMapping("/locations/by-owner/{ownerService}/{ownerEntityId}")
    public ResponseEntity<Map<String, Object>> locationsByOwner(
            @PathVariable String ownerService,
            @PathVariable String ownerEntityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.locationsByOwner(ownerService, ownerEntityId);
            return ResponseEntity.ok(Map.of("data", data != null ? data : List.of(), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila locations by-owner failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", List.of(), "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @GetMapping("/locations/{id}")
    public ResponseEntity<Map<String, Object>> getLocation(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.getLocation(id);
            if (data == null || data.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "LOCATION_NOT_FOUND", "message", "No Ndila location with that id"),
                        "meta", meta(requestId, correlationId)));
            }
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila get location failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @PostMapping("/catchments")
    public ResponseEntity<Map<String, Object>> createCatchment(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.createCatchment(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila create catchment failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    @PostMapping("/tracking/nearby")
    public ResponseEntity<Map<String, Object>> nearbyAssets(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.nearbyAssets(body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Ndila nearby assets failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", List.of(),
                    "error", Map.of("code", "NDILA_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    private static ResponseEntity<Map<String, Object>> unavailable(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "data", Map.of(),
                "error", Map.of("code", code, "message", message),
                "meta", meta(requestId, correlationId)));
    }

    private static Map<String, String> meta(String requestId, String correlationId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("request_id", requestId);
        m.put("correlation_id", correlationId);
        return m;
    }

    /** Rewrite ndila-owned tile templates onto this BFF's reachable, header-synthesised path. */
    private static void rewriteTileTemplates(JsonNode data) {
        if (!(data instanceof ObjectNode obj)) {
            return;
        }
        for (String field : List.of("tileUrlTemplate", "vectorTileUrlTemplate")) {
            JsonNode node = obj.get(field);
            if (node != null && node.isTextual()) {
                String rewritten = rewriteTilePath(node.asText());
                if (rewritten != null) {
                    obj.put(field, rewritten);
                }
            }
        }
    }

    private static String rewriteTilePath(String template) {
        if (template == null) {
            return null;
        }
        // Only rewrite the service's own tile namespaces; leave external provider URLs alone.
        if (template.startsWith("/api/v1/ndila/tiles/")) {
            return template.replaceFirst("^/api/v1/ndila/tiles/", "/internal/v1/ndila/tiles/");
        }
        if (template.startsWith("/api/v1/maps/tiles/")) {
            return template.replaceFirst("^/api/v1/maps/tiles/", "/internal/v1/ndila/tiles/");
        }
        return null;
    }
}
