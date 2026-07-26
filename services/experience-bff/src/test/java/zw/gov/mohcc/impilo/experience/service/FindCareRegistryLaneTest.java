package zw.gov.mohcc.impilo.experience.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR W5 guard tests — a registry listing must never be mistaken for an offered service.
 *
 * <p>The second find-care lane exists because 5,509 HPA-registered facilities carry no confirmed
 * capability, so a service-filtered search is structurally blind to them. Surfacing them is only
 * correct if they stay visibly distinct from a facility TUSO holds a live capability for.</p>
 */
class FindCareRegistryLaneTest {

    private static FindCareOrchestrationService.CareResult listing() {
        return new FindCareOrchestrationService.CareResult(
                1L, "uuid", "Some Surgery", "Clinic", null, "Buhera", "Manicaland",
                false, null, null, null, "UNKNOWN", true, null, null, null, null,
                FindCareOrchestrationService.CareResult.LANE_REGISTRY_LISTING,
                "IMPORTED_PENDING_CONFIGURATION");
    }

    private static FindCareOrchestrationService.CareResult serviceMatch() {
        return new FindCareOrchestrationService.CareResult(
                2L, "uuid2", "Real Pharmacy", "Pharmacy", null, "Buhera", "Manicaland",
                true, "PHARMACY", null, null, "OPERATING", true, null, null, null, null,
                FindCareOrchestrationService.CareResult.LANE_SERVICE_MATCH, null);
    }

    /**
     * The lane label and the serviceMatch flag must agree. A registry listing that reported
     * serviceMatch=true would sort among confirmed services and read as one.
     */
    @Test
    void aRegistryListingNeverClaimsAServiceMatch() {
        assertThat(listing().serviceMatch()).isFalse();
        assertThat(listing().matchedService()).isNull();
        assertThat(listing().resultLane())
                .isEqualTo(FindCareOrchestrationService.CareResult.LANE_REGISTRY_LISTING);
    }

    /** The two lanes are distinguishable without inspecting any other field. */
    @Test
    void theTwoLanesAreDistinct() {
        assertThat(listing().resultLane()).isNotEqualTo(serviceMatch().resultLane());
        assertThat(FindCareOrchestrationService.CareResult.LANE_REGISTRY_LISTING)
                .isEqualTo("REGISTRY_LISTING");
    }

    /** The listing carries its regulatory status, so the shell can say why it is unconfirmed. */
    @Test
    void aListingCarriesTheReasonItIsUnconfirmed() {
        assertThat(listing().regulatoryStatus()).isEqualTo("IMPORTED_PENDING_CONFIGURATION");
        assertThat(listing().operationalStatusUnverified()).isTrue();
    }

    /** The lane is a hint alongside real results, not a directory dump. */
    @Test
    void theLaneIsBounded() {
        assertThat(FindCareOrchestrationService.MAX_REGISTRY_LISTINGS).isBetween(1, 10);
    }
}
