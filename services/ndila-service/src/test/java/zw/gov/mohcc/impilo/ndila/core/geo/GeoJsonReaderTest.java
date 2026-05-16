package zw.gov.mohcc.impilo.ndila.core.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoJsonReaderTest {

    @Test
    void parsesPolygonAndContainsHarareCenter() {
        String polygon = "{\"type\":\"Polygon\",\"coordinates\":[[" +
                "[30.9,-17.9],[31.2,-17.9],[31.2,-17.7],[30.9,-17.7],[30.9,-17.9]]]}";
        GeoJsonReader.ParsedGeometry geom = GeoJsonReader.parse(polygon);
        assertThat(geom).isInstanceOf(GeoJsonReader.ParsedGeometry.Polygon.class);
        assertThat(geom.contains(new Coordinate(-17.8292, 31.0522))).isTrue();
        assertThat(geom.contains(new Coordinate(-17.6, 31.5))).isFalse();
    }

    @Test
    void parsesCircleExtension() {
        String circle = "{\"type\":\"Circle\",\"center\":[31.05,-17.83],\"radius_m\":1500}";
        GeoJsonReader.ParsedGeometry geom = GeoJsonReader.parse(circle);
        assertThat(geom).isInstanceOf(GeoJsonReader.ParsedGeometry.Circle.class);
        assertThat(geom.contains(new Coordinate(-17.83, 31.05))).isTrue();
        assertThat(geom.contains(new Coordinate(-17.90, 31.10))).isFalse();
    }

    @Test
    void unsupportedGeometryReturnsFalseSafely() {
        String junk = "{\"type\":\"GeometryCollection\"}";
        assertThat(GeoJsonReader.parse(junk).contains(new Coordinate(-17.83, 31.05))).isFalse();
    }
}
