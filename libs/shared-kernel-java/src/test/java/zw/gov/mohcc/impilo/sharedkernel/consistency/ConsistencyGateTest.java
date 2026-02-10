package zw.gov.mohcc.impilo.sharedkernel.consistency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ConsistencyGateTest {

    private final ConsistencyGate gate = new ConsistencyGate();

    @Test
    void msikaUpdateTariffRequiresSyncWithNationalSpine() {
        ConsistencyDecision decision = gate.evaluate(ActionType.MSIKA_UPDATE_TARIFF, "pod-1");
        assertTrue(decision.syncRequired());
        assertEquals("NATIONAL_SPINE_ONLY", decision.authority());
    }

    @Test
    void vitoPatientMergeRequiresSyncWithNationalSpine() {
        ConsistencyDecision decision = gate.evaluate(ActionType.VITO_PATIENT_MERGE, "pod-2");
        assertTrue(decision.syncRequired());
        assertEquals("NATIONAL_SPINE_ONLY", decision.authority());
    }

    @Test
    void controlledSubstancePrescriptionRequiresKernelPdp() {
        ConsistencyDecision decision = gate.evaluate(
                ActionType.CLINICAL_PRESCRIBE_CONTROLLED_SUBSTANCE, "pod-3");
        assertTrue(decision.syncRequired());
        assertEquals("KERNEL_PDP_REQUIRED", decision.authority());
    }

    @Test
    void unknownActionDoesNotRequireSync() {
        ConsistencyDecision decision = gate.evaluate(ActionType.UNKNOWN, "pod-4");
        assertFalse(decision.syncRequired());
        assertEquals("PROJECTION_OK", decision.authority());
    }

    @ParameterizedTest
    @EnumSource(ActionType.class)
    void allActionTypesReturnNonNullDecision(ActionType actionType) {
        ConsistencyDecision decision = gate.evaluate(actionType, "any-pod");
        assertNotNull(decision);
        assertNotNull(decision.authority());
    }

    @Test
    void nullActionTypeThrows() {
        assertThrows(NullPointerException.class,
                () -> gate.evaluate(null, "pod-1"));
    }
}
