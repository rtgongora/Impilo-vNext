package zw.gov.mohcc.impilo.ndila.core.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeoMathTest {

    @Test
    void haversineHararePariranyatwaToAvondale() {
        Coordinate harareCenter = new Coordinate(-17.8292, 31.0522);
        Coordinate avondale = new Coordinate(-17.8000, 31.0300);
        double meters = GeoMath.haversineMeters(harareCenter, avondale);
        assertThat(meters).isBetween(3_000.0, 5_500.0);
    }

    @Test
    void pointInRingRectangle() {
        List<Coordinate> ring = List.of(
                new Coordinate(-18.0, 31.0),
                new Coordinate(-18.0, 31.2),
                new Coordinate(-17.8, 31.2),
                new Coordinate(-17.8, 31.0),
                new Coordinate(-18.0, 31.0));
        assertThat(GeoMath.isPointInRing(new Coordinate(-17.9, 31.1), ring)).isTrue();
        assertThat(GeoMath.isPointInRing(new Coordinate(-17.6, 31.1), ring)).isFalse();
    }

    @Test
    void isWithinRadiusForHararePoints() {
        Coordinate center = new Coordinate(-17.8292, 31.0522);
        assertThat(GeoMath.isWithinRadius(center, 10_000, new Coordinate(-17.83, 31.05))).isTrue();
        assertThat(GeoMath.isWithinRadius(center, 1_000, new Coordinate(-17.90, 31.10))).isFalse();
    }
}
