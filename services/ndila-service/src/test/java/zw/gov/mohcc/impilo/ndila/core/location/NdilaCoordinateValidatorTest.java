package zw.gov.mohcc.impilo.ndila.core.location;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NdilaCoordinateValidatorTest {

    private final NdilaCoordinateValidator validator = new NdilaCoordinateValidator();

    @Test
    void hararecoordinatesAreValidAndPlausibleForZw() {
        var r = validator.validate(new BigDecimal("-17.8292"), new BigDecimal("31.0522"), null, "ZWE");
        assertThat(r.valid()).isTrue();
        assertThat(r.plausible()).isTrue();
        assertThat(r.confidence()).isGreaterThan(0.5);
    }

    @Test
    void zeroZeroCoordinatesAreSuspect() {
        var r = validator.validate(BigDecimal.ZERO, BigDecimal.ZERO, null, "ZWE");
        assertThat(r.valid()).isTrue();
        assertThat(r.plausible()).isFalse();
        assertThat(r.warnings()).anyMatch(w -> w.contains("ZERO"));
    }

    @Test
    void coordinatesOutsideZwAreFlagged() {
        var r = validator.validate(new BigDecimal("40.7128"), new BigDecimal("-74.0060"), null, "ZWE");
        assertThat(r.plausible()).isFalse();
        assertThat(r.warnings()).anyMatch(w -> w.contains("OUTSIDE_ZIMBABWE"));
    }

    @Test
    void missingCoordinatesAreInvalid() {
        var r = validator.validate(null, null, null, "ZWE");
        assertThat(r.valid()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.equals("MISSING_COORDINATES"));
    }

    @Test
    void lowAccuracyEmitsWarning() {
        var r = validator.validate(new BigDecimal("-17.83"), new BigDecimal("31.05"),
                new BigDecimal("800"), "ZWE");
        assertThat(r.warnings()).anyMatch(w -> w.equals("LOW_ACCURACY"));
    }
}
