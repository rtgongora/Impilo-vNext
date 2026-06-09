package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses and builds site geo_json payloads stored on {@link zw.gov.mohcc.impilo.indawo.domain.SiteEntity}.
 */
public final class SiteGeoExtractor {

    public record GeoPoint(BigDecimal latitude, BigDecimal longitude, String geocodeQuality) {}

    private SiteGeoExtractor() {}

    public static GeoPoint fromGeoJson(String geoJson, ObjectMapper mapper) {
        if (geoJson == null || geoJson.isBlank()) {
            return new GeoPoint(null, null, null);
        }
        try {
            JsonNode node = mapper.readTree(geoJson);
            if (node.has("type")
                    && "Point".equalsIgnoreCase(node.get("type").asText())
                    && node.has("coordinates")
                    && node.get("coordinates").isArray()
                    && node.get("coordinates").size() >= 2) {
                JsonNode coords = node.get("coordinates");
                return new GeoPoint(
                        decimalValue(coords.get(1)),
                        decimalValue(coords.get(0)),
                        textOrNull(node, "geocodeQuality", "geocode_quality"));
            }
            return new GeoPoint(
                    decimalOrNull(node, "latitude", "lat"),
                    decimalOrNull(node, "longitude", "lng", "lon"),
                    textOrNull(node, "geocodeQuality", "geocode_quality"));
        } catch (Exception ignored) {
            return new GeoPoint(null, null, null);
        }
    }

    public static String toGeoJson(
            BigDecimal latitude,
            BigDecimal longitude,
            String geocodeQuality,
            ObjectMapper mapper) throws JsonProcessingException {
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("type", "Point");
        geo.put("coordinates", List.of(longitude, latitude));
        geo.put("latitude", latitude);
        geo.put("longitude", longitude);
        if (geocodeQuality != null && !geocodeQuality.isBlank()) {
            geo.put("geocodeQuality", geocodeQuality);
        }
        return mapper.writeValueAsString(geo);
    }

    private static BigDecimal decimalOrNull(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return decimalValue(node.get(key));
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                String value = node.get(key).asText();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static BigDecimal decimalValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return BigDecimal.valueOf(node.asDouble());
    }
}
