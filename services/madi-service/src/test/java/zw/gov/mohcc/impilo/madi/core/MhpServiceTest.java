package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.MhpActivationEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.MhpPackEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.MhpActivationRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.MhpPackRepository;
import zw.gov.mohcc.impilo.sharedkernel.security.EmergencyAccessGuard;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import zw.gov.mohcc.impilo.sharedkernel.security.EscalationGrantValidator;

/**
 * MHP activation + pack issuance (madi V200, W8a). Proves the protocol-level layer reuses
 * {@link EmergencyAccessGuard} rather than re-implementing break-glass, and that every high-risk
 * action (activation AND each pack) is independently gated.
 */
@ExtendWith(MockitoExtension.class)
class MhpServiceTest {

    @Mock private MhpActivationRepository activationRepository;
    @Mock private MhpPackRepository packRepository;
    @Mock private MadiEventEmitter eventEmitter;


    /**
     * A guard whose trust plane confirms the grant. These tests are about MHP mechanics, not about
     * the grant branches — those are asserted directly in {@code EmergencyAccessGuardTest}, which
     * covers VALID / NO_GRANT / UNREACHABLE separately.
     */
    private final EmergencyAccessGuard grantedGuard = new EmergencyAccessGuard(
            (t, a, g) -> EscalationGrantValidator.Outcome.VALID, o -> { });

    private MhpService service;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MhpService(activationRepository, packRepository, eventEmitter, grantedGuard);
    }

    private MhpActivationEntity activeActivation(UUID id) {
        MhpActivationEntity a = new MhpActivationEntity();
        a.setActivationId(id);
        a.setTenantId(TENANT_ID);
        a.setStatus("ACTIVE");
        a.setPatientCpid("CPID-1");
        return a;
    }

    @Test
    void activate_underNonEmergencyPurpose_isDenied() {
        assertThatThrownBy(() -> service.activate(TENANT_ID, null, "CPID-1", null, null,
                "trauma-lead-A", "ROUTINE", "grant-1"))
                .isInstanceOf(EmergencyAccessGuard.EmergencyAccessDeniedException.class);
        verifyNoInteractions(activationRepository);
    }

    /** Mirrors Hibernate's client-side @GeneratedValue(UUID) — the id is set before save() returns. */
    private static void stubSaveGeneratesId(MhpActivationRepository repo) {
        when(repo.save(any())).thenAnswer(inv -> {
            MhpActivationEntity e = inv.getArgument(0);
            if (e.getActivationId() == null) e.setActivationId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void activate_underEmergencyPurpose_savesAndEmits() {
        stubSaveGeneratesId(activationRepository);

        MhpActivationEntity result = service.activate(TENANT_ID, null, null, null, null,
                "trauma-lead-A", "EMERGENCY", "grant-1");

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getActivatedBy()).isEqualTo("trauma-lead-A");
        verify(eventEmitter).emit(eq("MHP_ACTIVATION"), any(), eq("MHP_ACTIVATED"), any(), any(), any(), eq(TENANT_ID));
    }

    @Test
    void activate_toleratesAnUnidentifiedPatient() {
        stubSaveGeneratesId(activationRepository);
        MhpActivationEntity result = service.activate(TENANT_ID, null, null, null, null,
                "trauma-lead-A", "EMERGENCY", "grant-1");
        assertThat(result.getPatientCpid()).isNull();
    }

    @Test
    void issuePack_onAStoodDownActivation_isRefused() {
        UUID activationId = UUID.randomUUID();
        MhpActivationEntity stoodDown = activeActivation(activationId);
        stoodDown.setStatus("STOOD_DOWN");
        when(activationRepository.findByActivationIdAndTenantId(activationId, TENANT_ID))
                .thenReturn(Optional.of(stoodDown));

        assertThatThrownBy(() -> service.issuePack(activationId, TENANT_ID, 1, "RBC", 6, "O_NEG",
                "blood-bank-B", "EMERGENCY", "grant-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not ACTIVE");
        verifyNoInteractions(packRepository);
    }

    @Test
    void issuePack_underNonEmergencyPurpose_isDenied() {
        UUID activationId = UUID.randomUUID();
        when(activationRepository.findByActivationIdAndTenantId(activationId, TENANT_ID))
                .thenReturn(Optional.of(activeActivation(activationId)));

        assertThatThrownBy(() -> service.issuePack(activationId, TENANT_ID, 1, "RBC", 6, "O_NEG",
                "blood-bank-B", "ROUTINE", "grant-1"))
                .isInstanceOf(EmergencyAccessGuard.EmergencyAccessDeniedException.class);
    }

    @Test
    void issuePack_succeedsAndEmits() {
        UUID activationId = UUID.randomUUID();
        when(activationRepository.findByActivationIdAndTenantId(activationId, TENANT_ID))
                .thenReturn(Optional.of(activeActivation(activationId)));
        when(packRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MhpPackEntity pack = service.issuePack(activationId, TENANT_ID, 1, "RBC", 6, "O_NEG",
                "blood-bank-B", "EMERGENCY", "grant-1");

        assertThat(pack.getComponentType()).isEqualTo("RBC");
        assertThat(pack.getUnitsIssued()).isEqualTo(6);
        verify(eventEmitter).emit(eq("MHP_ACTIVATION"), any(), eq("MHP_PACK_ISSUED"), any(), any(), any(), eq(TENANT_ID));
    }

    @Test
    void issuePack_duplicateOfSamePackAndComponent_mapsToConflict() {
        UUID activationId = UUID.randomUUID();
        when(activationRepository.findByActivationIdAndTenantId(activationId, TENANT_ID))
                .thenReturn(Optional.of(activeActivation(activationId)));
        when(packRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.issuePack(activationId, TENANT_ID, 1, "RBC", 6, "O_NEG",
                "blood-bank-B", "EMERGENCY", "grant-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already issued");
    }

    @Test
    void standDown_requiresAReason() {
        UUID activationId = UUID.randomUUID();
        when(activationRepository.findByActivationIdAndTenantId(activationId, TENANT_ID))
                .thenReturn(Optional.of(activeActivation(activationId)));

        assertThatThrownBy(() -> service.standDown(activationId, TENANT_ID, "trauma-lead-A", ""))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("stand_down_reason");
    }

    @Test
    void standDown_alreadyStoodDown_isRefused() {
        UUID activationId = UUID.randomUUID();
        MhpActivationEntity stoodDown = activeActivation(activationId);
        stoodDown.setStatus("STOOD_DOWN");
        when(activationRepository.findByActivationIdAndTenantId(activationId, TENANT_ID))
                .thenReturn(Optional.of(stoodDown));

        assertThatThrownBy(() -> service.standDown(activationId, TENANT_ID, "trauma-lead-A", "resolved"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already");
    }

    @Test
    void standDown_withReason_succeedsAndEmits() {
        UUID activationId = UUID.randomUUID();
        when(activationRepository.findByActivationIdAndTenantId(activationId, TENANT_ID))
                .thenReturn(Optional.of(activeActivation(activationId)));
        when(activationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MhpActivationEntity result = service.standDown(activationId, TENANT_ID, "trauma-lead-A",
                "haemorrhage controlled");

        assertThat(result.getStatus()).isEqualTo("STOOD_DOWN");
        assertThat(result.getStandDownReason()).isEqualTo("haemorrhage controlled");
        verify(eventEmitter).emit(eq("MHP_ACTIVATION"), any(), eq("MHP_STOOD_DOWN"), any(), any(), any(), eq(TENANT_ID));
    }

    @Test
    void ratioSummary_sumsUnitsPerComponentAcrossPacks() {
        UUID activationId = UUID.randomUUID();
        when(activationRepository.findByActivationIdAndTenantId(activationId, TENANT_ID))
                .thenReturn(Optional.of(activeActivation(activationId)));
        MhpPackEntity p1 = new MhpPackEntity(); p1.setComponentType("RBC"); p1.setUnitsIssued(6);
        MhpPackEntity p2 = new MhpPackEntity(); p2.setComponentType("FFP"); p2.setUnitsIssued(4);
        MhpPackEntity p3 = new MhpPackEntity(); p3.setComponentType("RBC"); p3.setUnitsIssued(6);
        when(packRepository.findByActivationIdOrderByPackNumberAsc(activationId)).thenReturn(List.of(p1, p2, p3));

        Map<String, Integer> summary = service.ratioSummary(activationId, TENANT_ID);
        assertThat(summary).containsEntry("RBC", 12).containsEntry("FFP", 4);
    }
}
