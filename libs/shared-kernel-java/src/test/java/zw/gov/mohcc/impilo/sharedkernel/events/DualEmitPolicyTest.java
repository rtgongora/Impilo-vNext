package zw.gov.mohcc.impilo.sharedkernel.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DualEmitPolicyTest {

    private final DualEmitPolicy policy = new DualEmitPolicy();

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("EMIT_MODE");
    }

    @Test
    void defaultIsDualWhenUnset() {
        System.clearProperty("EMIT_MODE");
        assertEquals(EmitMode.DUAL, policy.mode());
        assertTrue(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }

    @Test
    void respectsLegacyOnly() {
        System.setProperty("EMIT_MODE", "LEGACY_ONLY");
        assertEquals(EmitMode.LEGACY_ONLY, policy.mode());
        assertTrue(policy.emitLegacy());
        assertFalse(policy.emitV11());
    }

    @Test
    void respectsV11Only() {
        System.setProperty("EMIT_MODE", "V1_1_ONLY");
        assertEquals(EmitMode.V1_1_ONLY, policy.mode());
        assertFalse(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }

    @Test
    void respectsDual() {
        System.setProperty("EMIT_MODE", "DUAL");
        assertEquals(EmitMode.DUAL, policy.mode());
        assertTrue(policy.emitLegacy());
        assertTrue(policy.emitV11());
    }

    @Test
    void caseInsensitive() {
        System.setProperty("EMIT_MODE", "legacy_only");
        assertEquals(EmitMode.LEGACY_ONLY, policy.mode());
    }

    @Test
    void fallsToDualOnInvalidValue() {
        System.setProperty("EMIT_MODE", "GARBAGE");
        assertEquals(EmitMode.DUAL, policy.mode());
    }

    @Test
    void fallsToDualOnBlankValue() {
        System.setProperty("EMIT_MODE", "   ");
        assertEquals(EmitMode.DUAL, policy.mode());
    }
}
