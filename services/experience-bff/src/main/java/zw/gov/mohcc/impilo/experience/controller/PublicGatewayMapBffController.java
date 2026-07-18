package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.NdilaServiceClient;

/**
 * Public gateway lane — self-hosted street map tiles for the citizen find-care
 * map (no authentication).
 *
 * <p>Pure binary passthrough of ndila's {@code PublicStreetTileController}
 * (self-hosted Martin street stack, OpenMapTiles-schema MVT). The payload is
 * national basemap street geometry only — PII-free by construction. Tiles are
 * high-frequency cacheable map assets, so like the other find-care reads this
 * lane is unmetered; long browser/edge caching (7 days) keeps the fetch volume
 * honest. Empty tiles pass through as 204 (MapLibre renders blank); when the
 * street stack is down the lane answers 502 and the map's admin-boundary base
 * layers keep rendering — never a fabricated tile, never a blank page.
 * Governed by docs/architecture/gateway-public-lane-security-adr.md.</p>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/map")
public class PublicGatewayMapBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayMapBffController.class);

    /** Public street pyramid tops out at z15 (mirrors ndila's public lane). */
    private static final int MAX_ZOOM = 15;

    private final NdilaServiceClient ndilaServiceClient;

    public PublicGatewayMapBffController(NdilaServiceClient ndilaServiceClient) {
        this.ndilaServiceClient = ndilaServiceClient;
    }

    @GetMapping("/tiles/{z}/{x}/{y}.mvt")
    public ResponseEntity<byte[]> tile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        if (z < 0 || z > MAX_ZOOM) {
            return ResponseEntity.badRequest().build();
        }
        long tilesPerAxis = 1L << z;
        if (x < 0 || y < 0 || x >= tilesPerAxis || y >= tilesPerAxis) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body;
        try {
            body = ndilaServiceClient.publicStreetTile(z, x, y);
        } catch (Exception e) {
            // Street stack disabled/unreachable (ndila 503) or any downstream fault.
            log.debug("Public street tile {}/{}/{} failed: {}", z, x, y, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        if (body == null) {
            // Empty tile passthrough — MapLibre treats 204 as blank.
            return ResponseEntity.noContent().build();
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                .contentType(MediaType.parseMediaType("application/x-protobuf"));
        if (isGzip(body)) {
            // Martin serves MVT pre-gzipped; declare the encoding on the untouched bytes.
            builder.header(HttpHeaders.CONTENT_ENCODING, "gzip");
        }
        return builder.body(body);
    }

    private static boolean isGzip(byte[] body) {
        return body.length >= 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B;
    }
}
