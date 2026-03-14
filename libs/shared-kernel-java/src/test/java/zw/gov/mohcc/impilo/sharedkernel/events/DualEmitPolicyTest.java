package zw.gov.mohcc.impilo.sharedkernel.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DualEmitPolicyTest {

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("EMIT_MODE");
    }

    // ── No-arg constructor (backward compat) ──

    @Test
    void defaultIsDualWhenUnset() {
        DualEmitPolicy policy = new DualEmitPolicy();
        System.clearProperty("EMIT_MODE");
        assertEquals(EmitMode.DUAL, policy.mode());
        assertTrue(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }

    @Test
    void respectsLegacyOnly() {
        DualEmitPolicy policy = new DualEmitPolicy();
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");
        assertEquals(EmitMode.LEGACY_ONLY, policy.mode());
        assertTrue(policy.emitLegacy());
        assertFalse(policy.emitV11());
    }

    @Test
    void respectsV11Only() {
        DualEmitPolicy policy = new DualEmitPolicy();
        System.setProperty("EMIT_MODE", "V1_1_ONLY");
        assertEquals(EmitMode.V1_1_ONLY, policy.mode());
        assertFalse(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }

    @Test
    void respectsDual() {
        DualEmitPolicy policy = new DualEmitPolicy();
        System.setProperty("EMIT_MODE", "DUAL");
        assertEquals(EmitMode.DUAL, policy.mode());
        assertTrue(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }

    @Test
    void caseInsensitive() {
        DualEmitPolicy policy = new DualEmitPolicy();
        System.setProperty("EMIT_MODE", "legacy_only");
        assertEquals(EmitMode.LEGACY_ONLY, policy.mode());
    }

    @Test
    void fallsToDualOnInvalidValue() {
        DualEmitPolicy policy = new DualEmitPolicy();
        System.setProperty("EMIT_MODE", "GARBAGE");
        assertEquals(EmitMode.DUAL, policy.mode());
    }

    @Test
    void fallsToDualOnBlankValue() {
        DualEmitPolicy policy = new DualEmitPolicy();
        System.setProperty("EMIT_MODE", "   ");
        assertEquals(EmitMode.DUAL, policy.mode());
    }

    // ── Config fallback constructor (new precedence) ──

    @Test
    void configFallbackUsedWhenNoEnvOrProp() {
        DualEmitPolicy policy = new DualEmitPolicy("V1_1_ONLY");
        System.clearProperty("EMIT_MODE");
        assertEquals(EmitMode.V1_1_ONLY, policy.mode());
    }

    @Test
    void systemPropertyOverridesConfigFallback() {
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");
        DualEmitPolicy policy = new DualEmitPolicy("V1_1_ONLY");
        assertEquals(EmitMode.LEGACY_ONLY, policy.mode());
    }

    @Test
    void configFallbackIgnoredWhenInvalid() {
        DualEmitPolicy policy = new DualEmitPolicy("GARBAGE");
        System.clearProperty("EMIT_MODE");
        assertEquals(EmitMode.DUAL, policy.mode());
    }

    @Test
    void nullConfigFallbackDefaultsToDual() {
        DualEmitPolicy policy = new DualEmitPolicy(null);
        System.clearProperty("EMIT_MODE");
        assertEquals(EmitMode.DUAL, policy.mode());
    }

    @Test
    void blankConfigFallbackDefaultsToDual() {
        DualEmitPolicy policy = new DualEmitPolicy("   ");
        System.clearProperty("EMIT_MODE");
        assertEquals(EmitMode.DUAL, policy.mode());
    }
}
