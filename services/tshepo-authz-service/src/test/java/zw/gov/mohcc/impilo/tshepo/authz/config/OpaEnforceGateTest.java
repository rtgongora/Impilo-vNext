package zw.gov.mohcc.impilo.tshepo.authz.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ENFORCE must be unreachable on the strength of a mode flag alone.
 *
 * <p>The risk is concrete: {@code opa-mode} is now bound to an environment variable, so anyone who
 * can set one could otherwise promote an unvalidated policy corpus to authoritative in a
 * deployment where no comparison was ever run. The gate makes the promotion cite its proof.</p>
 */
class OpaEnforceGateTest {

    @Test
    @DisplayName("ENFORCE with no cited evidence refuses to start")
    void enforceWithoutEvidenceRefuses() {
        assertThatThrownBy(() -> OpaEnforceGate.validate("ENFORCE", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parity-evidence-path");

        assertThatThrownBy(() -> OpaEnforceGate.validate("ENFORCE", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ENFORCE citing evidence that does not exist refuses to start")
    void enforceWithMissingEvidenceRefuses(@TempDir Path tmp) {
        Path absent = tmp.resolve("parity-report-that-was-never-produced.md");
        assertThatThrownBy(() -> OpaEnforceGate.validate("ENFORCE", absent.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no such file exists");
    }

    @Test
    @DisplayName("ENFORCE citing a directory is not accepted as evidence")
    void enforceWithDirectoryRefuses(@TempDir Path tmp) {
        assertThatThrownBy(() -> OpaEnforceGate.validate("ENFORCE", tmp.toString()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ENFORCE citing real evidence is permitted — the gate is not merely a blanket refusal")
    void enforceWithEvidenceIsPermitted(@TempDir Path tmp) throws Exception {
        Path evidence = Files.writeString(tmp.resolve("parity.md"), "0 unexplained divergences");
        assertThatCode(() -> OpaEnforceGate.validate("ENFORCE", evidence.toString()))
                .as("a gate that can never pass proves nothing about the gate")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OFF and SHADOW start without evidence")
    void nonEnforceModesNeedNoEvidence() {
        assertThatCode(() -> OpaEnforceGate.validate("OFF", "")).doesNotThrowAnyException();
        assertThatCode(() -> OpaEnforceGate.validate("SHADOW", "")).doesNotThrowAnyException();
        assertThatCode(() -> OpaEnforceGate.validate("shadow", "")).doesNotThrowAnyException();
        assertThatCode(() -> OpaEnforceGate.validate(null, "")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unrecognised mode is refused, not silently treated as OFF")
    void typoIsRefused() {
        // "SHADOWW" silently falling back to OFF would disable the comparison invisibly, which is
        // exactly how a shadow programme ends up with no data and nobody noticing.
        assertThatThrownBy(() -> OpaEnforceGate.validate("SHADOWW", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected one of");
    }

    @Test
    @DisplayName("the valid-mode set is exactly OFF, SHADOW, ENFORCE")
    void modeVocabularyIsClosed() {
        assertThat(OpaEnforceGate.VALID_MODES).containsExactlyInAnyOrder("OFF", "SHADOW", "ENFORCE");
    }
}
