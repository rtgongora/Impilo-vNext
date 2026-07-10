package zw.gov.mohcc.impilo.ndila.core.tiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NdilaStreetTileProxyServiceTest {

    @Test
    void isActiveWhenOsmEnabledWithBaseUrl() {
        NdilaStreetTileProxyService service = new NdilaStreetTileProxyService(
                null, true, "http://martin:3000/zimbabwe/{z}/{x}/{y}");
        assertTrue(service.isActive());
        assertEquals(
                "http://martin:3000/zimbabwe/12/2048/1365",
                service.buildTileUrl(12, 2048, 1365));
    }

    @Test
    void pngSniffRejectsVectorBytes() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] gzipMvt = new byte[] {0x1F, (byte) 0x8B, 0x08, 0x00, 0, 0, 0, 0};
        assertTrue(NdilaStreetTileProxyService.isPng(png));
        assertFalse(NdilaStreetTileProxyService.isPng(gzipMvt));
        assertTrue(NdilaStreetTileProxyService.isGzip(gzipMvt));
        assertFalse(NdilaStreetTileProxyService.isGzip(png));
    }

    @Test
    void inactiveForMockOrBlankUrls() {
        NdilaStreetTileProxyService mock = new NdilaStreetTileProxyService(
                null, true, "mock://tiles/{z}/{x}/{y}.png");
        assertFalse(mock.isActive());

        NdilaStreetTileProxyService disabled = new NdilaStreetTileProxyService(
                null, false, "http://martin:3000/zimbabwe/{z}/{x}/{y}");
        assertFalse(disabled.isActive());
    }
}
