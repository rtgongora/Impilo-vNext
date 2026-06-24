package zw.gov.mohcc.impilo.clinical.terminology;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UcumUnitConverterTest {

    private final UcumUnitConverter converter = new UcumUnitConverter();

    private double convert(String value, String from, String to) {
        Optional<BigDecimal> r = converter.convert(new BigDecimal(value), from, to);
        assertThat(r).as("%s %s -> %s", value, from, to).isPresent();
        return r.get().doubleValue();
    }

    @Test
    void identity_sameUnit_returnsValue() {
        assertThat(converter.convert(new BigDecimal("5.4"), "mmol/L", "mmol/L")).contains(new BigDecimal("5.4"));
    }

    @Test
    void massConcentration_dimensionallyConsistent() {
        assertThat(convert("100", "mg/dL", "g/L")).isEqualTo(1.0, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(convert("12", "g/dL", "g/L")).isEqualTo(120.0, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void molarScaling() {
        assertThat(convert("5", "mmol/L", "umol/L")).isEqualTo(5000.0, org.assertj.core.api.Assertions.within(1e-6));
    }

    @Test
    void clinicalUnitAliases_normalise() {
        // mmHg alias + identity
        assertThat(converter.isComparable("mmHg", "mm[Hg]")).isTrue();
        // °C alias
        assertThat(converter.isComparable("°C", "Cel")).isTrue();
    }

    @Test
    void massVsMolar_withoutMolarMass_failsClosed() {
        // mg/dL and mmol/L are dimensionally different — must NOT silently convert.
        assertThat(converter.convert(new BigDecimal("90"), "mg/dL", "mmol/L")).isEmpty();
        assertThat(converter.isComparable("mg/dL", "mmol/L")).isFalse();
    }

    @Test
    void massVsMolar_withMolarMass_bridges() {
        // Glucose molar mass ~180.16 g/mol. 90 mg/dL ≈ 5.0 mmol/L.
        Optional<BigDecimal> r = converter.convertWithMolarMass(new BigDecimal("90"), "mg/dL", "mmol/L", 180.16);
        assertThat(r).isPresent();
        assertThat(r.get().doubleValue()).isEqualTo(5.0, org.assertj.core.api.Assertions.within(0.05));
    }

    @Test
    void unknownUnit_failsClosed() {
        assertThat(converter.convert(new BigDecimal("1"), "flibbles", "g/L")).isEmpty();
    }
}
