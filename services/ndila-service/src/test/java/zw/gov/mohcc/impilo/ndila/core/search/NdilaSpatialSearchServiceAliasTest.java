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

    /**
     * HAR W4 — the alias map normalises TOWARDS the stored vocabulary, so whatever it converts to
     * must be exactly what writers store. When the V003 seed stored {@code FACILITY} instead, those
     * rows became permanently unmatched by proximity search: the caller's {@code FACILITY} was
     * rewritten to {@code HEALTH_FACILITY} before the query ran. Pinning both ends here means a
     * change to either one alone fails the build rather than silently hiding half the estate.
     */
    @Test
    void normalizeLocationType_agreesWithTheStoredVocabulary() {
        assertEquals(
                zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationVocabulary.LOCATION_TYPE_HEALTH_FACILITY,
                NdilaSpatialSearchService.normalizeLocationType("FACILITY"));
        assertEquals(
                zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationVocabulary.LOCATION_TYPE_HEALTH_FACILITY,
                NdilaSpatialSearchService.normalizeLocationType("CLINIC"));
    }

    @Test
    void normalizeLocationTypes_nullWhenEmpty() {
        assertNull(NdilaSpatialSearchService.normalizeLocationTypes(null));
        assertNull(NdilaSpatialSearchService.normalizeLocationTypes(List.of()));
    }
}
