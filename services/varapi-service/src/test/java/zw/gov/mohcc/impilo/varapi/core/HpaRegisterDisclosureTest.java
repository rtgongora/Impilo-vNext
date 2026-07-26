package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.varapi.api.dto.PublicPractitionerVerificationResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR W2 guard tests — making an HPA-listed registration FINDABLE must never make it look VERIFIED.
 *
 * <p>The backfill exists because ~4,241 practitioners returned NOT_FOUND on every public lookup.
 * Fixing that is only correct if the answer a citizen now gets is honest about its provenance: HPA
 * listed this person as the practitioner in charge of a registered institution; their own
 * professional council has not confirmed it, and nothing here asserts a current practising
 * certificate.</p>
 */
class HpaRegisterDisclosureTest {

    /**
     * The status the backfill writes must sit outside every vocabulary that any gate treats as a
     * verified registry. If this constant is ever changed to something like REGISTERED, a bulk
     * import would silently start satisfying verification checks.
     */
    @Test
    void backfillStatusIsNotAVerifiedRegistryValue() {
        String status = HpaPractitionerRegisterBackfillService.STATUS_LISTED_PENDING_COUNCIL_VERIFICATION;
        assertThat(status).isEqualTo("LISTED_PENDING_COUNCIL_VERIFICATION");
        assertThat(status)
                .as("an HPA institution return must never read as a council registration")
                .isNotEqualTo("REGISTERED")
                .isNotEqualTo("REGISTERED_ACTIVE")
                .isNotEqualTo("MATCHED_BOTH")
                .isNotEqualTo("ACTIVE");
    }

    /** The record must claim no statutory register and no currency dates it was never given. */
    @Test
    void backfillClaimsNoRegisterAndNoDates() {
        assertThat(HpaPractitionerRegisterBackfillService.ISSUED_UNDER_AUTHORITY)
                .contains("Health Professions Authority")
                .contains("institution return");
        assertThat(HpaPractitionerRegisterBackfillService.SOURCE_REFERENCE)
                .contains("hpa_practitioner_candidate");
    }

    /** A miss must stay uniform, and must not accidentally read as council-verified. */
    @Test
    void notFoundIsNeverCouncilVerified() {
        PublicPractitionerVerificationResponse miss = PublicPractitionerVerificationResponse.notFound();
        assertThat(miss.registerStatus()).isEqualTo(PublicPractitionerVerificationResponse.STATUS_NOT_FOUND);
        assertThat(miss.councilVerified()).isFalse();
        assertThat(miss.registerSource()).isNull();
        assertThat(miss.displayName()).isNull();
        assertThat(miss.registrationNumber()).isNull();
    }
}
