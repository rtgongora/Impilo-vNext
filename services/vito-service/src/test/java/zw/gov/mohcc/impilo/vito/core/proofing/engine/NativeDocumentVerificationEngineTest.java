package zw.gov.mohcc.impilo.vito.core.proofing.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The native MRZ engine is a real deterministic capability, so it is worth proving against
 * the ICAO 9303 standard (not just self-consistently).
 */
@DisplayName("NativeDocumentVerificationEngine (ICAO 9303 MRZ)")
class NativeDocumentVerificationEngineTest {

    private final NativeDocumentVerificationEngine engine = new NativeDocumentVerificationEngine();

    @Test
    @DisplayName("check-digit algorithm matches published ICAO 9303 values")
    void checkDigitMatchesIcaoSpec() {
        // Canonical worked examples from ICAO Doc 9303 Part 3.
        assertThat(NativeDocumentVerificationEngine.computeCheckDigit("L898902C3")).isEqualTo(6);
        assertThat(NativeDocumentVerificationEngine.computeCheckDigit("740812")).isEqualTo(2);
        assertThat(NativeDocumentVerificationEngine.computeCheckDigit("120415")).isEqualTo(9);
    }

    @Test
    @DisplayName("valid TD3 passport MRZ → VERIFIED (capped confidence)")
    void validTd3Verified() {
        // ICAO 9303 TD3 specimen line 2.
        String line2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
        ProofingSignal s = engine.verifyDocument(new DocumentEvidence(
                "PASSPORT", List.of("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", line2), null, null));
        assertThat(s.isVerified()).isTrue();
        assertThat(s.confidence()).isLessThanOrEqualTo(0.6); // integrity, not authenticity
    }

    @Test
    @DisplayName("a mistyped/altered check digit → REJECTED")
    void corruptedTd3Rejected() {
        // Same specimen with the DOB check digit flipped 2 → 3.
        String line2 = "L898902C36UTO7408123F1204159ZE184226B<<<<<10";
        ProofingSignal s = engine.verifyDocument(new DocumentEvidence(
                "PASSPORT", List.of("P<UTO...", line2), null, null));
        assertThat(s.isRejected()).isTrue();
        assertThat(s.result()).isEqualTo(ProofingSignal.REJECTED);
    }

    @Test
    @DisplayName("no MRZ present → INCONCLUSIVE (escalate, never fabricate a pass)")
    void noMrzInconclusive() {
        ProofingSignal s = engine.verifyDocument(new DocumentEvidence(
                "BIRTH_CERTIFICATE", null, null, List.of("doc-service://scan-1")));
        assertThat(s.result()).isEqualTo(ProofingSignal.INCONCLUSIVE);
    }

    @Test
    @DisplayName("valid TD1 identity-card MRZ → VERIFIED")
    void validTd1Verified() {
        // ICAO 9303 TD1 specimen (three 30-char lines).
        ProofingSignal s = engine.verifyDocument(new DocumentEvidence(
                "NATIONAL_ID",
                List.of("I<UTOD231458907<<<<<<<<<<<<<<<",
                        "7408122F1204159UTO<<<<<<<<<<<6",
                        "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"),
                null, null));
        assertThat(s.isVerified()).isTrue();
    }
}
