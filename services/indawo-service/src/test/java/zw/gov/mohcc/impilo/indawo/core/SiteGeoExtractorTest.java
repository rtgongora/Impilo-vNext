package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SiteGeoExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fromGeoJson_parsesFlatLatitudeLongitude() throws Exception {
        String json = "{\"latitude\":-17.82,\"longitude\":31.05,\"geocodeQuality\":\"MANUAL\"}";
        SiteGeoExtractor.GeoPoint point = SiteGeoExtractor.fromGeoJson(json, mapper);
        assertEquals(new BigDecimal("-17.82"), point.latitude());
        assertEquals(new BigDecimal("31.05"), point.longitude());
        assertEquals("MANUAL", point.geocodeQuality());
    }

    @Test
    void fromGeoJson_parsesGeoJsonPoint() throws Exception {
        String json = "{\"type\":\"Point\",\"coordinates\":[31.05,-17.82],\"geocodeQuality\":\"HIGH\"}";
        SiteGeoExtractor.GeoPoint point = SiteGeoExtractor.fromGeoJson(json, mapper);
        assertEquals(new BigDecimal("-17.82"), point.latitude());
        assertEquals(new BigDecimal("31.05"), point.longitude());
        assertEquals("HIGH", point.geocodeQuality());
    }

    @Test
    void toGeoJson_roundTrip() throws Exception {
        String json = SiteGeoExtractor.toGeoJson(
                new BigDecimal("-17.5"),
                new BigDecimal("31.1"),
                "MANUAL",
                mapper);
        SiteGeoExtractor.GeoPoint point = SiteGeoExtractor.fromGeoJson(json, mapper);
        assertNotNull(point.latitude());
        assertNotNull(point.longitude());
        assertEquals("MANUAL", point.geocodeQuality());
    }
}
