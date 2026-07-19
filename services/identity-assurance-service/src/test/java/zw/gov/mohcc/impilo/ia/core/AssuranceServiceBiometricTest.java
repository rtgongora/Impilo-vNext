package zw.gov.mohcc.impilo.ia.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.ia.persistence.entity.AssuranceRecordEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.AssuranceRecordRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.AssuranceUpgradeRequestRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient;

/**
 * Biometric-binding assurance signal through the shared ABIS seam: a live probe is
 * verified against the subject's enrolled template. MATCH → the actor is bound at
 * LOA4 (only ever raising) and the provenance audited; NO_MATCH → explicit denial,
 * no elevation; engine UNAVAILABLE → no elevation, existing level unchanged.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssuranceServiceBiometricTest {

    @Mock private AssuranceRecordRepository recordRepository;
    @Mock private AssuranceUpgradeRequestRepository upgradeRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private BiometricVerificationClient biometricVerification;

    private AssuranceService service;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AssuranceService(recordRepository, upgradeRepository, outboxRepository, biometricVerification);
    }

    @Test
    void match_bindsActorAtLoa4_andAudits() {
        // Stateful record store so the final getStatus re-read reflects the save (as real JPA would).
        AtomicReference<AssuranceRecordEntity> stored = new AtomicReference<>();
        when(recordRepository.findByTenantIdAndActorId(tenant, "alice"))
                .thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(recordRepository.save(any())).thenAnswer(i -> {
            AssuranceRecordEntity e = i.getArgument(0);
            stored.set(e);
            return e;
        });
        when(biometricVerification.verify(eq("subj-alice"), eq("FINGERPRINT"), any()))
                .thenReturn(new BiometricVerificationClient.Decision("MATCH", 0.99));

        AssuranceService.StatusView view = service.recordBiometricBinding(
                tenant, "alice", "subj-alice", "FINGERPRINT", "cHJvYmU=");

        assertThat(view.currentLevel()).isEqualTo(AssuranceLevel.LOA4);
        assertThat(stored.get().getCurrentLevel()).isEqualTo(AssuranceLevel.LOA4);
        verify(outboxRepository).save(any()); // provenance audited
    }

    @Test
    void noMatch_isExplicitDenial_noElevation() {
        when(recordRepository.findByTenantIdAndActorId(tenant, "alice")).thenReturn(Optional.empty());
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(new BiometricVerificationClient.Decision("NO_MATCH", 0.02));

        assertThatThrownBy(() -> service.recordBiometricBinding(
                tenant, "alice", "subj-alice", "FINGERPRINT", "cHJvYmU="))
                .isInstanceOf(SecurityException.class);

        verify(recordRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void unavailable_noElevation_levelUnchanged() {
        when(recordRepository.findByTenantIdAndActorId(tenant, "alice")).thenReturn(Optional.empty());
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(BiometricVerificationClient.Decision.unavailable());

        AssuranceService.StatusView view = service.recordBiometricBinding(
                tenant, "alice", "subj-alice", "FINGERPRINT", "cHJvYmU=");

        // No enrolled record + biometric outage → stays at the LOA1 default, nothing raised.
        assertThat(view.currentLevel()).isEqualTo(AssuranceLevel.LOA1);
        verify(recordRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void match_neverDowngrades_alreadyLoa4() {
        AssuranceRecordEntity high = new AssuranceRecordEntity();
        high.setTenantId(tenant);
        high.setActorId("alice");
        high.setCurrentLevel(AssuranceLevel.LOA4);
        when(recordRepository.findByTenantIdAndActorId(tenant, "alice")).thenReturn(Optional.of(high));
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(new BiometricVerificationClient.Decision("MATCH", 0.97));

        AssuranceService.StatusView view = service.recordBiometricBinding(
                tenant, "alice", "subj-alice", "FINGERPRINT", "cHJvYmU=");

        assertThat(view.currentLevel()).isEqualTo(AssuranceLevel.LOA4);
        // Already at LOA4 → no re-raise, no duplicate audit.
        verify(recordRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void missingProbe_throws() {
        assertThatThrownBy(() -> service.recordBiometricBinding(
                tenant, "alice", "subj-alice", "FINGERPRINT", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(biometricVerification, never()).verify(any(), any(), any());
    }
}
