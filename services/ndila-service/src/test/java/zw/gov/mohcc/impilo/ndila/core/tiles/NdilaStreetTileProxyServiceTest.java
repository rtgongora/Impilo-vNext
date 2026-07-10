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
    void inactiveForMockOrBlankUrls() {
        NdilaStreetTileProxyService mock = new NdilaStreetTileProxyService(
                null, true, "mock://tiles/{z}/{x}/{y}.png");
        assertFalse(mock.isActive());

        NdilaStreetTileProxyService disabled = new NdilaStreetTileProxyService(
                null, false, "http://martin:3000/zimbabwe/{z}/{x}/{y}");
        assertFalse(disabled.isActive());
    }
}
