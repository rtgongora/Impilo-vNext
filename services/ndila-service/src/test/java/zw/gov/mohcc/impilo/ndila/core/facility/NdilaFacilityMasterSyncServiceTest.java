package zw.gov.mohcc.impilo.ndila.core.facility;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NdilaFacilityMasterSyncServiceTest {

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (cwd.endsWith("ndila-service")) {
            return cwd.getParent().getParent();
        }
        return cwd;
    }

    @Test
    void generatedGeoJsonHasValidCoordinateOrder() throws Exception {
        Path geojson = repoRoot().resolve("docs/data/facility-master-2024-07-23/generated/ndila_health_facilities.geojson");
        @SuppressWarnings("unchecked")
        Map<String, Object> collection = new ObjectMapper().readValue(geojson.toFile(), Map.class);
        assertThat(collection.get("type")).isEqualTo("FeatureCollection");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> features = (List<Map<String, Object>>) collection.get("features");
        assertThat(features).isNotEmpty();
        for (Map<String, Object> feature : features) {
            @SuppressWarnings("unchecked")
            Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
            @SuppressWarnings("unchecked")
            List<Number> coordinates = (List<Number>) geometry.get("coordinates");
            double lon = coordinates.get(0).doubleValue();
            double lat = coordinates.get(1).doubleValue();
            assertThat(lat).isBetween(-90.0, 90.0);
            assertThat(lon).isBetween(-180.0, 180.0);
        }
    }

    @Test
    void generatedSeedCoordinateCountMatchesGeoJsonFeatures() throws Exception {
        Path seed = repoRoot().resolve("docs/data/facility-master-2024-07-23/generated/ndila_facility_location_seed.json");
        Path geojson = repoRoot().resolve("docs/data/facility-master-2024-07-23/generated/ndila_health_facilities.geojson");
        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = mapper.readValue(seed.toFile(), List.class);
        long withCoords = records.stream().filter(r -> Boolean.TRUE.equals(r.get("has_coordinates"))).count();
        @SuppressWarnings("unchecked")
        Map<String, Object> collection = mapper.readValue(geojson.toFile(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> features = (List<Map<String, Object>>) collection.get("features");
        assertThat(features).hasSize((int) withCoords);
    }
}
