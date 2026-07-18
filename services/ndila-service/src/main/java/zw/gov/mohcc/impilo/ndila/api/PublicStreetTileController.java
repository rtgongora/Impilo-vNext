package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ndila.core.tiles.NdilaStreetTileProxyService;
import zw.gov.mohcc.impilo.ndila.core.tiles.NdilaStreetTileProxyService.VectorTileResult;
import zw.gov.mohcc.impilo.ndila.core.tiles.NdilaStreetTileProxyService.VectorTileStatus;

/**
 * Public (anonymous) street-tile lane — governed by
 * docs/architecture/gateway-public-lane-security-adr.md (§3: the BFF public
 * gateway may only call a downstream {@code Public*Controller}).
 *
 * <p>Serves self-hosted OpenMapTiles-schema vector tiles (Martin street stack)
 * for the citizen find-care map. Public-safe by construction: the payload is
 * street geometry from the national basemap only — no PII, no facility or
 * person data ever flows through this path. Honest failure modes: an empty
 * tile is 204, and when the street stack is disabled or unreachable the lane
 * answers 503 — never a fabricated tile.</p>
 */
@RestController
public class PublicStreetTileController {

    /** Public street pyramid tops out at z15 (Martin/planetiler maxzoom; MapLibre overzooms). */
    private static final int MAX_ZOOM = 15;

    private final NdilaStreetTileProxyService streetProxy;

    public PublicStreetTileController(NdilaStreetTileProxyService streetProxy) {
        this.streetProxy = streetProxy;
    }

    @GetMapping("/v1/public/ndila/tiles/{z}/{x}/{y}.mvt")
    public ResponseEntity<byte[]> vectorTile(
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
        VectorTileResult result = streetProxy.fetchVectorResult(z, x, y);
        if (result.status() == VectorTileStatus.UNAVAILABLE) {
            // Street stack disabled or unreachable — honest 503, no fabricated tiles.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (result.status() == VectorTileStatus.EMPTY) {
            // Nothing at this coordinate (Martin 204/404) — MapLibre treats 204 as blank.
            return ResponseEntity.noContent().build();
        }
        byte[] body = result.body();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                .contentType(MediaType.parseMediaType("application/x-protobuf"));
        if (NdilaStreetTileProxyService.isGzip(body)) {
            // Martin serves MVT pre-gzipped; forward the bytes as-is with the encoding declared.
            builder.header(HttpHeaders.CONTENT_ENCODING, "gzip");
        }
        return builder.body(body);
    }
}
