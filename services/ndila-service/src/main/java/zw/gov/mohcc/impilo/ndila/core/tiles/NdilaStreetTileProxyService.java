package zw.gov.mohcc.impilo.ndila.core.tiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Proxies tiles from a self-hosted OSM-derived tile server (Martin,
 * tileserver-gl, Mapbox-compatible XYZ) through Ndila's sovereign tile path.
 * Martin serves gzipped MVT vector tiles; those flow through the vector path.
 * The raster path only forwards bytes that really are PNG so the sovereign
 * preview raster fallback stays intact when the upstream is vector-only.
 */
@Service
public class NdilaStreetTileProxyService {

    private static final Logger log = LoggerFactory.getLogger(NdilaStreetTileProxyService.class);

    private final RestClient restClient;
    private final boolean tilesEnabled;
    private final String tileBaseUrl;

    public NdilaStreetTileProxyService(
            RestClient ndilaOsmRestClient,
            @Value("${ndila.providers.osm.enabled:false}") boolean tilesEnabled,
            @Value("${ndila.providers.osm.tile-base-url:}") String tileBaseUrl) {
        this.restClient = ndilaOsmRestClient;
        this.tilesEnabled = tilesEnabled;
        this.tileBaseUrl = tileBaseUrl == null ? "" : tileBaseUrl.trim();
    }

    public boolean isActive() {
        return tilesEnabled && !tileBaseUrl.isBlank() && !tileBaseUrl.startsWith("mock://");
    }

    /** Only returns bytes that carry a real PNG signature — never mislabelled vector data. */
    public Optional<byte[]> fetchPng(int z, int x, int y) {
        return fetchTile(z, x, y).filter(NdilaStreetTileProxyService::isPng);
    }

    /** Raw upstream tile bytes (Martin: gzipped MVT). Empty tiles (HTTP 204) map to empty. */
    public Optional<byte[]> fetchVector(int z, int x, int y) {
        return fetchTile(z, x, y).filter(body -> !isPng(body));
    }

    public static boolean isPng(byte[] body) {
        return body.length >= 8
                && (body[0] & 0xFF) == 0x89 && body[1] == 'P' && body[2] == 'N' && body[3] == 'G';
    }

    public static boolean isGzip(byte[] body) {
        return body.length >= 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B;
    }

    private Optional<byte[]> fetchTile(int z, int x, int y) {
        if (!isActive()) {
            return Optional.empty();
        }
        String url = buildTileUrl(z, x, y);
        try {
            byte[] body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length < 8) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                log.debug("Ndila street tile proxy {} -> HTTP {}", url, ex.getStatusCode().value());
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.debug("Ndila street tile proxy failed for {}: {}", url, ex.toString());
            return Optional.empty();
        }
    }

    String buildTileUrl(int z, int x, int y) {
        return tileBaseUrl
                .replace("{z}", Integer.toString(z))
                .replace("{x}", Integer.toString(x))
                .replace("{y}", Integer.toString(y));
    }
}
