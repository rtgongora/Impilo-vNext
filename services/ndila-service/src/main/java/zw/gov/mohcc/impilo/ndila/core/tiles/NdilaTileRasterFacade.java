package zw.gov.mohcc.impilo.ndila.core.tiles;

import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Serves map tiles for browser clients: self-hosted street rasters when
 * configured, otherwise sovereign preview PNGs.
 */
@Service
public class NdilaTileRasterFacade {

    private final NdilaStreetTileProxyService streetProxy;
    private final NdilaPreviewTileRasterService previewRaster;

    public NdilaTileRasterFacade(
            NdilaStreetTileProxyService streetProxy,
            NdilaPreviewTileRasterService previewRaster) {
        this.streetProxy = streetProxy;
        this.previewRaster = previewRaster;
    }

    public byte[] renderPng(int z, int x, int y) throws IOException {
        if (streetProxy.isActive()) {
            var proxied = streetProxy.fetchPng(z, x, y);
            if (proxied.isPresent()) {
                return proxied.get();
            }
        }
        return previewRaster.renderPng(z, x, y);
    }

    /** Raw upstream vector tile bytes (gzipped MVT from Martin); empty when inactive or blank tile. */
    public java.util.Optional<byte[]> fetchVectorTile(int z, int x, int y) {
        if (!streetProxy.isActive()) {
            return java.util.Optional.empty();
        }
        return streetProxy.fetchVector(z, x, y);
    }

    public boolean streetTilesActive() {
        return streetProxy.isActive();
    }
}
