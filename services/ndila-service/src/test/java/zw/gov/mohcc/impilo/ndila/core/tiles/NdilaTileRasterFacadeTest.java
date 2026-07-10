package zw.gov.mohcc.impilo.ndila.core.tiles;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NdilaTileRasterFacadeTest {

    @Test
    void fallsBackToPreviewWhenStreetProxyInactive() throws IOException {
        NdilaStreetTileProxyService proxy = new NdilaStreetTileProxyService(
                null, false, "");
        NdilaPreviewTileRasterService preview = new NdilaPreviewTileRasterService();
        NdilaTileRasterFacade facade = new NdilaTileRasterFacade(proxy, preview);

        byte[] png = facade.renderPng(10, 512, 341);
        assertTrue(png.length > 100);
        assertTrue(png[0] == (byte) 0x89);
    }
}
