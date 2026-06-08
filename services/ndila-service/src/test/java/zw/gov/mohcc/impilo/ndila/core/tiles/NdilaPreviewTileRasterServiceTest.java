package zw.gov.mohcc.impilo.ndila.core.tiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NdilaPreviewTileRasterServiceTest {

    @Test
    void renderPngProducesNonEmptyBytes() throws Exception {
        NdilaPreviewTileRasterService service = new NdilaPreviewTileRasterService();
        byte[] png = service.renderPng(12, 2048, 1365);
        assertTrue(png.length > 100);
        assertTrue(png[0] == (byte) 0x89);
    }
}
