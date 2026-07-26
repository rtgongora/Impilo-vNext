package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR W1 — the regulator's asserted status must map onto the platform's legitimacy vocabulary
 * faithfully, and an unknown or absent assertion must never be read as "registered".
 */
class HpaLegitimacyStatusTest {

    @Test
    void currentRegistrationsMapToRegisteredCurrent() {
        for (String raw : new String[]{"REGISTERED", "registered", "Registered Current", "CURRENT",
                "active", "VALID", "  registered  "}) {
            assertThat(HpaEnrichmentImportService.hpaLegitimacyStatus(raw))
                    .as("HPA regulatory_status %s", raw)
                    .isEqualTo(FacilityLegitimacyStatus.REGISTERED_CURRENT);
        }
    }

    @Test
    void adverseStatusesAreCarriedThroughFaithfully() {
        assertThat(HpaEnrichmentImportService.hpaLegitimacyStatus("EXPIRED"))
                .isEqualTo(FacilityLegitimacyStatus.EXPIRED);
        assertThat(HpaEnrichmentImportService.hpaLegitimacyStatus("lapsed"))
                .isEqualTo(FacilityLegitimacyStatus.EXPIRED);
        assertThat(HpaEnrichmentImportService.hpaLegitimacyStatus("SUSPENDED"))
                .isEqualTo(FacilityLegitimacyStatus.SUSPENDED);
        assertThat(HpaEnrichmentImportService.hpaLegitimacyStatus("non-compliant"))
                .isEqualTo(FacilityLegitimacyStatus.KNOWN_NOT_COMPLIANT);
    }

    /**
     * The load-bearing case: a blank, null or unrecognised regulatory status must fall to
     * PENDING_VERIFICATION, never REGISTERED_CURRENT — otherwise a facility with no asserted
     * registration would be granted an allowing verdict it never earned.
     */
    @Test
    void unknownOrAbsentNeverReadsAsRegistered() {
        for (String raw : new String[]{null, "", "   ", "SOMETHING_ELSE", "PENDING", "UNKNOWN"}) {
            assertThat(HpaEnrichmentImportService.hpaLegitimacyStatus(raw))
                    .as("HPA regulatory_status %s", raw)
                    .isEqualTo(FacilityLegitimacyStatus.PENDING_VERIFICATION);
        }
    }

    /**
     * No HPA regulatory_status value may map to a status the platform treats as a positive
     * government exception — that verdict requires an explicit human decision with a reason, and
     * must never arrive from a bulk import.
     */
    @Test
    void importNeverProducesAGovernmentOperationalException() {
        for (String raw : new String[]{null, "", "REGISTERED", "EXPIRED", "SUSPENDED",
                "GOVERNMENT_OPERATIONAL_EXCEPTION", "government operational exception"}) {
            assertThat(HpaEnrichmentImportService.hpaLegitimacyStatus(raw))
                    .as("HPA regulatory_status %s", raw)
                    .isNotEqualTo(FacilityLegitimacyStatus.GOVERNMENT_OPERATIONAL_EXCEPTION);
        }
    }
}
