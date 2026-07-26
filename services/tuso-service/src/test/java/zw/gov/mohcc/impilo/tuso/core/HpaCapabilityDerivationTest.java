package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HAR W5 guard tests — the line between "the licence IS the service" and a guess.
 *
 * <p>5,509 HPA-listed facilities carry zero capabilities, which makes every service-filtered search
 * blind to them. The cheap fix is to infer services from the institution type. That is exactly how
 * a woman in labour is sent to a "clinic" that has never delivered a baby. These tests pin the
 * narrow rule that was chosen instead.</p>
 */
class HpaCapabilityDerivationTest {

    // ── what the licence genuinely is ───────────────────────────────────────

    @Test
    void aRegisteredPharmacyIsAPharmacy() {
        assertThat(HpaCapabilityDerivationService.deriveCapability("Pharmacy", "Retail Pharmacy"))
                .get().extracting(c -> c[0]).isEqualTo("PHARMACY");
    }

    @Test
    void licenceClassesThatAreTheServiceAllDerive() {
        assertThat(HpaCapabilityDerivationService.deriveCapability("Diagnostic Services", "Radiology Centres"))
                .get().extracting(c -> c[0]).isEqualTo("RADIOLOGY");
        assertThat(HpaCapabilityDerivationService.deriveCapability("Diagnostic Services", "X-Ray Rooms"))
                .get().extracting(c -> c[0]).isEqualTo("RADIOLOGY");
        assertThat(HpaCapabilityDerivationService.deriveCapability("Laboratory", "Multidisciplinary Laboratory"))
                .get().extracting(c -> c[0]).isEqualTo("LABORATORY");
        assertThat(HpaCapabilityDerivationService.deriveCapability(
                "Consulting Rooms / Surgery", "Dental Surgeries ( General Practice)"))
                .get().extracting(c -> c[0]).isEqualTo("DENTAL");
        assertThat(HpaCapabilityDerivationService.deriveCapability("Consulting Rooms / Surgery", "Optical Rooms"))
                .get().extracting(c -> c[0]).isEqualTo("OPTOMETRY");
        assertThat(HpaCapabilityDerivationService.deriveCapability("Ambulance", "Ambulance Services"))
                .get().extracting(c -> c[0]).isEqualTo("AMBULANCE");
    }

    // ── the four that may never be inferred ─────────────────────────────────

    /**
     * HPA's own register contains "Maternity Homes" and "Emergency Rooms". The register naming a
     * place is not confirmation that it is staffed, equipped and open tonight — and these are the
     * services where being wrong costs a life rather than a wasted trip.
     */
    @Test
    void maternityAndEmergencyAreNeverDerivedEvenThoughTheRegisterNamesThem() {
        assertThat(HpaCapabilityDerivationService.deriveCapability("Clinic", "Maternity Homes")).isEmpty();
        assertThat(HpaCapabilityDerivationService.deriveCapability("Clinic", "Emergency Rooms")).isEmpty();
        assertThat(HpaCapabilityDerivationService.deriveCapability("Hospital", "Hospital up to 50 Beds")).isEmpty();
        assertThat(HpaCapabilityDerivationService.deriveCapability("Clinic", "Clinics ( Outpatients Only)")).isEmpty();
        assertThat(HpaCapabilityDerivationService.deriveCapability("Nursing Home", null)).isEmpty();
    }

    /** No mapping in the tables may ever produce a forbidden token, now or after an edit. */
    @Test
    void noMappingCanEverEmitAForbiddenToken() {
        java.util.stream.Stream.concat(
                        HpaCapabilityDerivationService.SUBTYPE_CAPABILITIES.values().stream(),
                        HpaCapabilityDerivationService.TYPE_CAPABILITIES.values().stream())
                .map(c -> c[0].toUpperCase(Locale.ROOT))
                .forEach(code -> assertThat(HpaCapabilityDerivationService.FORBIDDEN_TOKENS)
                        .as("derivable capability %s", code)
                        .doesNotContain(code));
    }

    /** And the runtime assertion has to actually refuse, not just document the intent. */
    @Test
    void theRuntimeGuardRefusesAForbiddenToken() {
        assertThatThrownBy(() -> HpaCapabilityDerivationService.assertNotForbidden("MATERNITY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never inference");
        assertThatThrownBy(() -> HpaCapabilityDerivationService.assertNotForbidden("emergency"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── places that carry a derivable parent type but serve no citizen ───────

    @Test
    void aWholesalerIsNotAPharmacyAndAResearchLabIsNotALaboratory() {
        // Sending someone to collect their medicine from a wholesaler sends them to a warehouse.
        assertThat(HpaCapabilityDerivationService.deriveCapability("Pharmaceutical", "Pharmaceutical Wholesalers"))
                .isEmpty();
        assertThat(HpaCapabilityDerivationService.deriveCapability("Pharmaceutical", null)).isEmpty();
        // A research laboratory does not accept a citizen's specimen.
        assertThat(HpaCapabilityDerivationService.deriveCapability("Laboratory", "Research Laboratory")).isEmpty();
    }

    // ── provenance ──────────────────────────────────────────────────────────

    /** Everything derived must be marked unconfirmed, or the surface cannot label it honestly. */
    @Test
    void derivedCapabilitiesDeclareThemselvesUnconfirmed() {
        String metadata = HpaCapabilityDerivationService.derivedMetadataJson();
        assertThat(metadata).contains("\"derived\":true");
        assertThat(metadata).contains("\"confirmed\":false");
        assertThat(metadata).contains(HpaCapabilityDerivationService.DERIVATION_SOURCE);
    }
}
