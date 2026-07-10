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

    public boolean streetTilesActive() {
        return streetProxy.isActive();
    }
}
