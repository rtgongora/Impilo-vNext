package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR Wave 0 guard tests — the invariants that keep a regulator's institution listing from being
 * mistaken for an operating facility. These encode decisions, not implementation detail: if one of
 * them fails, a change has quietly granted ~5,509 HPA-listed facilities a capability they have not
 * earned.
 */
class HarSafetyHardeningTest {

    /**
     * S3 — "clinic" is not evidence of a maternity service. The regulator's institution types
     * include dental, optometry and radiology clinics; inferring maternity from a free-text type
     * string would advertise obstetric care to a citizen on no evidence at all.
     */
    @Test
    void clinicNeverInfersMaternity() {
        for (String type : List.of("CLINIC", "DENTAL CLINIC", "OPTOMETRY CLINIC", "RADIOLOGY CLINIC",
                "RURAL_HEALTH_CENTRE", "PHARMACY", "LABORATORY")) {
            List<String[]> caps = FacilityOperationalizationService.defaultCapabilities(type);
            assertThat(caps).as("capabilities derived for %s", type)
                    .noneMatch(c -> "MATERNITY".equals(c[0]))
                    .noneMatch(c -> "IPD".equals(c[0]));
            assertThat(caps).as("outpatient is the only honest default for %s", type)
                    .extracting(c -> c[0]).containsExactly("OPD");
        }
    }

    /** S3 — an explicit hospital keeps its inpatient/maternity derivation. */
    @Test
    void hospitalStillDerivesInpatientAndMaternity() {
        List<String[]> caps = FacilityOperationalizationService.defaultCapabilities("DISTRICT HOSPITAL");
        assertThat(caps).extracting(c -> c[0]).containsExactly("OPD", "IPD", "MATERNITY");
    }

    /** S3 — a null/blank type must never widen the derivation. */
    @Test
    void unknownTypeGetsOutpatientOnly() {
        assertThat(FacilityOperationalizationService.defaultCapabilities(null))
                .extracting(c -> c[0]).containsExactly("OPD");
        assertThat(FacilityOperationalizationService.defaultCapabilities(""))
                .extracting(c -> c[0]).containsExactly("OPD");
    }
}
