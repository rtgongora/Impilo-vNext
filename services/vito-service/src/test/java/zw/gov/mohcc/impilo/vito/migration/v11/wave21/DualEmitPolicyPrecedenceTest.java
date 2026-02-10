package zw.gov.mohcc.impilo.vito.migration.v11.wave21;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import zw.gov.mohcc.impilo.sharedkernel.events.EmitMode;
import zw.gov.mohcc.impilo.vito.events.VitoEventMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test B: DualEmitPolicy precedence.
 *
 * Proves that EMIT_MODE system property overrides the vito.v11.emit-mode yml value.
 * No Spring context required — exercises VitoEventMapper.resolveEmitMode() directly.
 */
@DisplayName("Wave 2.1 — DualEmitPolicy precedence (EMIT_MODE overrides yml)")
class DualEmitPolicyPrecedenceTest {

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("EMIT_MODE");
    }

    @Test
    @DisplayName("EMIT_MODE system property overrides yml value")
    void systemPropertyOverridesYml() {
        System.setProperty("EMIT_MODE", "V1_1_ONLY");

        // yml says LEGACY_ONLY, but system property says V1_1_ONLY
        EmitMode result = VitoEventMapper.resolveEmitMode("LEGACY_ONLY");
        assertEquals(EmitMode.V1_1_ONLY, result,
                "EMIT_MODE system property must override yml value");
    }

    @Test
    @DisplayName("yml value used when EMIT_MODE is not set")
    void ymlUsedWhenEnvNotSet() {
        // No system property, no env var
        EmitMode result = VitoEventMapper.resolveEmitMode("LEGACY_ONLY");
        assertEquals(EmitMode.LEGACY_ONLY, result,
                "yml value must be used when EMIT_MODE env/prop is not set");
    }

    @Test
    @DisplayName("DUAL is default when neither EMIT_MODE nor yml is set")
    void defaultIsDualWhenNothingSet() {
        EmitMode result = VitoEventMapper.resolveEmitMode(null);
        assertEquals(EmitMode.DUAL, result,
                "Default must be DUAL when nothing is set");
    }

    @Test
    @DisplayName("Invalid yml value falls through to DUAL default")
    void invalidYmlFallsThroughToDefault() {
        EmitMode result = VitoEventMapper.resolveEmitMode("INVALID_VALUE");
        assertEquals(EmitMode.DUAL, result,
                "Invalid yml value must fall through to DUAL");
    }

    @Test
    @DisplayName("emitLegacy returns true for LEGACY_ONLY and DUAL")
    void emitLegacyContract() {
        assertTrue(VitoEventMapper.emitLegacy(EmitMode.LEGACY_ONLY));
        assertTrue(VitoEventMapper.emitLegacy(EmitMode.DUAL));
        assertFalse(VitoEventMapper.emitLegacy(EmitMode.V1_1_ONLY));
    }

    @Test
    @DisplayName("emitV11 returns true for V1_1_ONLY and DUAL")
    void emitV11Contract() {
        assertTrue(VitoEventMapper.emitV11(EmitMode.V1_1_ONLY));
        assertTrue(VitoEventMapper.emitV11(EmitMode.DUAL));
        assertFalse(VitoEventMapper.emitV11(EmitMode.LEGACY_ONLY));
    }
}
