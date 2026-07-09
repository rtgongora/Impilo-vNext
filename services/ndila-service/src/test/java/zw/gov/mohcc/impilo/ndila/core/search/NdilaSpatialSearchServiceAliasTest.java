package zw.gov.mohcc.impilo.ndila.core.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NdilaSpatialSearchServiceAliasTest {

    @Test
    void normalizeLocationType_mapsFacilityAlias() {
        assertEquals("HEALTH_FACILITY", NdilaSpatialSearchService.normalizeLocationType("FACILITY"));
        assertEquals("HEALTH_FACILITY", NdilaSpatialSearchService.normalizeLocationType("facility"));
    }

    @Test
    void normalizeLocationTypes_deduplicatesAliases() {
        assertEquals(
                List.of("HEALTH_FACILITY", "PHARMACY"),
                NdilaSpatialSearchService.normalizeLocationTypes(List.of("FACILITY", "HEALTH_FACILITY", "PHARMACY")));
    }

    @Test
    void normalizeLocationTypes_nullWhenEmpty() {
        assertNull(NdilaSpatialSearchService.normalizeLocationTypes(null));
        assertNull(NdilaSpatialSearchService.normalizeLocationTypes(List.of()));
    }
}
