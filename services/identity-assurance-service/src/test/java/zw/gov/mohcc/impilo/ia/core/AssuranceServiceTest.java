package zw.gov.mohcc.impilo.ia.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.ia.persistence.entity.AssuranceRecordEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.AssuranceUpgradeRequestEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.AssuranceRecordRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.AssuranceUpgradeRequestRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssuranceServiceTest {

    @Mock private AssuranceRecordRepository recordRepository;
    @Mock private AssuranceUpgradeRequestRepository upgradeRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private AssuranceService service;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AssuranceService(recordRepository, upgradeRepository, outboxRepository);
        when(upgradeRepository.save(any())).thenAnswer(inv -> {
            AssuranceUpgradeRequestEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });
        when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private AssuranceUpgradeRequestEntity pendingRequest(String actor, AssuranceLevel target) {
        AssuranceUpgradeRequestEntity r = new AssuranceUpgradeRequestEntity();
        r.setId(7L);
        r.setTenantId(tenant);
        r.setActorId(actor);
        r.setCurrentLevel(AssuranceLevel.LOA1);
        r.setTargetLevel(target);
        r.setMethod("IN_PERSON_VERIFICATION");
        r.setStatus(UpgradeStatus.PENDING);
        return r;
    }

    @Test
    void getStatus_noRecord_defaultsLoa1_withRestrictedHighFriction() {
        when(recordRepository.findByTenantIdAndActorId(tenant, "alice")).thenReturn(Optional.empty());
        AssuranceService.StatusView view = service.getStatus(tenant, "alice");
        assertThat(view.currentLevel()).isEqualTo(AssuranceLevel.LOA1);
        assertThat(view.grantedPermissions()).contains("VIEW_OWN_RECORDS");
        assertThat(view.restrictedPermissions()).contains("PRESCRIBING", "CLAIMS_SUBMISSION");
    }

    @Test
    void requestUpgrade_targetNotHigher_isRejected() {
        when(recordRepository.findByTenantIdAndActorId(tenant, "alice")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requestUpgrade(tenant, "alice", AssuranceLevel.LOA1, "IN_PERSON_VERIFICATION"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestUpgrade_valid_createsPending() {
        when(recordRepository.findByTenantIdAndActorId(tenant, "alice")).thenReturn(Optional.empty());
        AssuranceUpgradeRequestEntity r = service.requestUpgrade(tenant, "alice", AssuranceLevel.LOA3, "IN_PERSON_VERIFICATION");
        assertThat(r.getStatus()).isEqualTo(UpgradeStatus.PENDING);
        assertThat(r.getTargetLevel()).isEqualTo(AssuranceLevel.LOA3);
    }

    @Test
    void decide_reviewerIsRequester_failsDualControl() {
        when(upgradeRepository.findByIdAndTenantId(7L, tenant)).thenReturn(Optional.of(pendingRequest("alice", AssuranceLevel.LOA3)));
        assertThatThrownBy(() -> service.decideUpgrade(tenant, "alice", 7L, true, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void decide_approve_raisesActorLevel() {
        when(upgradeRepository.findByIdAndTenantId(7L, tenant)).thenReturn(Optional.of(pendingRequest("alice", AssuranceLevel.LOA3)));
        when(recordRepository.findByTenantIdAndActorId(tenant, "alice")).thenReturn(Optional.empty());

        AssuranceUpgradeRequestEntity decided = service.decideUpgrade(tenant, "reviewer-bob", 7L, true, null);
        assertThat(decided.getStatus()).isEqualTo(UpgradeStatus.APPROVED);

        ArgumentCaptor<AssuranceRecordEntity> captor = ArgumentCaptor.forClass(AssuranceRecordEntity.class);
        verify(recordRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentLevel()).isEqualTo(AssuranceLevel.LOA3);
    }

    @Test
    void decide_reject_requiresReason() {
        when(upgradeRepository.findByIdAndTenantId(7L, tenant)).thenReturn(Optional.of(pendingRequest("alice", AssuranceLevel.LOA3)));
        assertThatThrownBy(() -> service.decideUpgrade(tenant, "reviewer-bob", 7L, false, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decide_reject_withReason_setsRejected_andDoesNotRaiseLevel() {
        when(upgradeRepository.findByIdAndTenantId(7L, tenant)).thenReturn(Optional.of(pendingRequest("alice", AssuranceLevel.LOA3)));
        AssuranceUpgradeRequestEntity decided = service.decideUpgrade(tenant, "reviewer-bob", 7L, false, "Insufficient documents");
        assertThat(decided.getStatus()).isEqualTo(UpgradeStatus.REJECTED);
        assertThat(decided.getReason()).isEqualTo("Insufficient documents");
        verify(recordRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void decide_notPending_isRejected() {
        AssuranceUpgradeRequestEntity already = pendingRequest("alice", AssuranceLevel.LOA3);
        already.setStatus(UpgradeStatus.APPROVED);
        when(upgradeRepository.findByIdAndTenantId(7L, tenant)).thenReturn(Optional.of(already));
        assertThatThrownBy(() -> service.decideUpgrade(tenant, "reviewer-bob", 7L, true, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
