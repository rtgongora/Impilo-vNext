package zw.gov.mohcc.impilo.sharedkernel.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The emergency break-glass guard: emergency purpose grants an audited override, else it denies. */
class EmergencyAccessGuardTest {

    @Test
    void emergencyAndBreakGlassPurposesAreRecognised() {
        assertTrue(EmergencyAccessGuard.isEmergencyPurpose("EMERGENCY"));
        assertTrue(EmergencyAccessGuard.isEmergencyPurpose("break_glass"));
        assertFalse(EmergencyAccessGuard.isEmergencyPurpose("TREATMENT"));
        assertFalse(EmergencyAccessGuard.isEmergencyPurpose(null));
    }

    @Test
    void breakGlassGrantedUnderEmergencyPurpose() {
        EmergencyAccessGuard.Decision d =
                EmergencyAccessGuard.requireBreakGlass("EMERGENCY", "O_NEG_EMERGENCY_RELEASE", "HID-1", "actor-1");
        assertTrue(d.breakGlass());
        assertEquals("BREAK_GLASS", d.mode());
        assertEquals("O_NEG_EMERGENCY_RELEASE", d.action());
    }

    @Test
    void breakGlassDeniedWithoutEmergencyPurpose() {
        assertThrows(EmergencyAccessGuard.EmergencyAccessDeniedException.class,
                () -> EmergencyAccessGuard.requireBreakGlass("TREATMENT", "O_NEG_EMERGENCY_RELEASE", "HID-1", "actor-1"));
        assertThrows(EmergencyAccessGuard.EmergencyAccessDeniedException.class,
                () -> EmergencyAccessGuard.requireBreakGlass(null, "O_NEG_EMERGENCY_RELEASE", "HID-1", "actor-1"));
    }
}
