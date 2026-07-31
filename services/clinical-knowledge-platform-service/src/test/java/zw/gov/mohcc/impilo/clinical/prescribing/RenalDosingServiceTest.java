package zw.gov.mohcc.impilo.clinical.prescribing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenalDosingServiceTest {

    private final RenalDosingService service = new RenalDosingService();

    @Test
    void missingEgfrSurfacesUnknownThroughTheService() {
        var data = service.advise(null, "NSAID");

        assertThat(data.get("level")).isEqualTo("UNKNOWN");
        assertThat(data.get("computed")).isEqualTo(false);
        assertThat(data.get("message")).asString().contains("eGFR is not available");
    }

    @Test
    void knownInputsReturnComputedAdvice() {
        var data = service.advise(25.0, "BIGUANIDE");

        assertThat(data.get("computed")).isEqualTo(true);
        assertThat(data.get("level")).isEqualTo("ADJUST");
        assertThat(data.get("drug_class_display")).isEqualTo("Biguanide (metformin)");
    }
}
