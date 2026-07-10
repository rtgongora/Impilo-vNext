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
 * Proxies raster tiles from a self-hosted OSM-derived tile server (Martin,
 * tileserver-gl, Mapbox-compatible XYZ) through Ndila's sovereign tile path.
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

    public Optional<byte[]> fetchPng(int z, int x, int y) {
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
