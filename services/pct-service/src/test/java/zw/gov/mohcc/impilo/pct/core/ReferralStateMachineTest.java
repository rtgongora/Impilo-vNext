package zw.gov.mohcc.impilo.pct.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralTransitionGuardProperties;
import zw.gov.mohcc.impilo.pct.domain.ReferralStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralTransitionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralTransitionRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferralStateMachineTest {

    private ReferralTransitionRepository ledger;
    private TelemetryService telemetry;
    private ReferralTransitionGuardProperties props;
    private ReferralStateMachine sm;

    @BeforeEach
    void setUp() {
        ledger = mock(ReferralTransitionRepository.class);
        when(ledger.save(any())).thenAnswer(i -> i.getArgument(0));
        telemetry = mock(TelemetryService.class);
        props = new ReferralTransitionGuardProperties();
        sm = new ReferralStateMachine(ledger, telemetry, props);
    }

    private ReferralEntity referral(String status) {
        ReferralEntity e = new ReferralEntity();
        e.setReferralId(UUID.randomUUID());
        e.setTenantId(UUID.randomUUID());
        e.setStatus(status);
        return e;
    }

    // ---- allowed edges (matrix + compat) ----

    @Test
    void allowsTheLiveHappyPathIncludingAcceptToCompleteCompatEdge() {
        assertTrue(sm.isAllowed(Optional.of(ReferralStatus.DRAFT), ReferralStatus.SUBMITTED));
        assertTrue(sm.isAllowed(Optional.of(ReferralStatus.SUBMITTED), ReferralStatus.ACCEPTED));
        assertTrue(sm.isAllowed(Optional.of(ReferralStatus.ACCEPTED), ReferralStatus.RESPONDED));
        // J-VC-1 runtime-proven compat edge that spec §11.3 omits:
        assertTrue(sm.isAllowed(Optional.of(ReferralStatus.ACCEPTED), ReferralStatus.COMPLETED));
    }

    @Test
    void selfTransitionIsAllowedNoOp() {
        // MESSAGE respond keeps the current status; must never be rejected.
        assertTrue(sm.isAllowed(Optional.of(ReferralStatus.SUBMITTED), ReferralStatus.SUBMITTED));
        assertTrue(sm.isAllowed(Optional.of(ReferralStatus.ACCEPTED), ReferralStatus.ACCEPTED));
    }

    @Test
    void unknownLegacySourceStatusResolvesLeniently() {
        assertTrue(sm.isAllowed(Optional.empty(), ReferralStatus.SUBMITTED));
    }

    // ---- prohibited edges ----

    @Test
    void rejectsIllegalEdges() {
        assertFalse(sm.isAllowed(Optional.of(ReferralStatus.DRAFT), ReferralStatus.COMPLETED));
        assertFalse(sm.isAllowed(Optional.of(ReferralStatus.COMPLETED), ReferralStatus.ACCEPTED));
        assertFalse(sm.isAllowed(Optional.of(ReferralStatus.CANCELLED), ReferralStatus.SUBMITTED));
        assertFalse(sm.isAllowed(Optional.of(ReferralStatus.DRAFT), ReferralStatus.ACCEPTED));
    }

    @Test
    void allowExtraEscapeHatchPermitsAnEdgeWithoutMatrixChange() {
        assertFalse(sm.isAllowed(Optional.of(ReferralStatus.DRAFT), ReferralStatus.RESPONDED));
        props.setAllowExtra(java.util.List.of("DRAFT->RESPONDED"));
        assertTrue(sm.isAllowed(Optional.of(ReferralStatus.DRAFT), ReferralStatus.RESPONDED));
    }

    // ---- shadow vs enforce ----

    @Test
    void shadowModeRecordsViolationButAppliesTargetAnyway() {
        props.setMode(ReferralTransitionGuardProperties.Mode.SHADOW);
        ReferralEntity e = referral("DRAFT");

        sm.apply(e, ReferralStatus.COMPLETED, "complete"); // illegal, but shadow

        assertEquals("COMPLETED", e.getStatus()); // applied — live path not blocked
        ArgumentCaptor<ReferralTransitionEntity> cap = ArgumentCaptor.forClass(ReferralTransitionEntity.class);
        verify(ledger).save(cap.capture());
        assertFalse(cap.getValue().isAllowed());
        assertEquals("SHADOW", cap.getValue().getMode());
    }

    @Test
    void enforceModeRejectsIllegalTransitionWith409AndDoesNotMutate() {
        props.setMode(ReferralTransitionGuardProperties.Mode.ENFORCE);
        ReferralEntity e = referral("DRAFT");

        PctDomainException ex = assertThrows(PctDomainException.class,
                () -> sm.apply(e, ReferralStatus.COMPLETED, "complete"));

        assertEquals("ILLEGAL_TRANSITION", ex.getCode());
        assertEquals(409, ex.getStatus());
        assertEquals("DRAFT", e.getStatus()); // unchanged on rejection
    }

    @Test
    void enforceModeAllowsLegalTransition() {
        props.setMode(ReferralTransitionGuardProperties.Mode.ENFORCE);
        ReferralEntity e = referral("SUBMITTED");
        sm.apply(e, ReferralStatus.ACCEPTED, "accept");
        assertEquals("ACCEPTED", e.getStatus());
    }

    @Test
    void offModeAppliesWithoutLedger() {
        props.setMode(ReferralTransitionGuardProperties.Mode.OFF);
        ReferralEntity e = referral("DRAFT");
        sm.apply(e, ReferralStatus.COMPLETED, "complete"); // illegal, but OFF = kill switch
        assertEquals("COMPLETED", e.getStatus());
        verify(ledger, never()).save(any());
    }

    // ---- raw passthrough (updateReferralStage backdoor) ----

    @Test
    void applyRawToleratesUnknownTargetStringInShadow() {
        props.setMode(ReferralTransitionGuardProperties.Mode.SHADOW);
        ReferralEntity e = referral("SUBMITTED");
        sm.applyRaw(e, "SOME_LEGACY_STATE", "stage-update");
        assertEquals("SOME_LEGACY_STATE", e.getStatus());
    }

    @Test
    void applyRawRoutesKnownTargetThroughTheGuard() {
        props.setMode(ReferralTransitionGuardProperties.Mode.ENFORCE);
        ReferralEntity e = referral("DRAFT");
        // DRAFT->COMPLETED is illegal even via the raw backdoor when enforcing.
        assertThrows(PctDomainException.class, () -> sm.applyRaw(e, "completed", "stage-update"));
    }

    @Test
    void ledgerFailureNeverBreaksTheTransition() {
        // The clinical mutation must survive a ledger write failure (best-effort audit).
        when(ledger.save(any())).thenThrow(new RuntimeException("db down"));
        props.setMode(ReferralTransitionGuardProperties.Mode.SHADOW);
        ReferralEntity e = referral("SUBMITTED");
        sm.apply(e, ReferralStatus.ACCEPTED, "accept");
        assertEquals("ACCEPTED", e.getStatus());
    }
}
