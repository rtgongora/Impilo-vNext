package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.SettlementEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.SettlementStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.LedgerEntryRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PayoutBatchRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PayoutItemRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.SettlementRepository;
import zw.gov.mohcc.impilo.mushex.service.disbursement.DisbursementRailRegistry;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Optional biometric step-up on high-value settlement release: above the step-up
 * threshold with a probe supplied, MATCH → release marked biometric-stepped-up;
 * NO_MATCH → release blocked (nothing released); engine UNAVAILABLE → falls back to
 * the existing non-biometric step-up factor (not auto-approved by the biometric layer).
 * Below threshold or absent probe ⇒ unchanged.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementReleaseBiometricTest {

    @Mock SettlementRepository settlementRepository;
    @Mock PayoutBatchRepository batchRepository;
    @Mock PayoutItemRepository itemRepository;
    @Mock PaymentIntentRepository intentRepository;
    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock LedgerService ledgerService;
    @Mock EventOutboxRepository outboxRepository;
    @Mock BiometricVerificationClient biometricVerification;

    private final UUID tenantId = UUID.randomUUID();
    private SettlementService service;

    @BeforeEach
    void setUp() {
        when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(batchRepository.findBySettlementId(any())).thenReturn(List.of());
        DisbursementRailRegistry registry = new DisbursementRailRegistry(List.of());
        service = new SettlementService(settlementRepository, batchRepository, itemRepository,
                intentRepository, ledgerEntryRepository, ledgerService, outboxRepository,
                new ObjectMapper(), registry, new MushexProperties(), biometricVerification);
        TrustContextHolder.set(new TrustContext(
                tenantId, "ops-1", "SYSTEM_ADMIN", "FINANCE",
                "device-1", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    /** COMPUTED settlement whose totals JSON carries the given release amount (totalPaid). */
    private SettlementEntity computed(String id, String totalPaid) {
        SettlementEntity s = new SettlementEntity();
        s.setSettlementId(id);
        s.setTenantId(tenantId);
        s.setStatus(SettlementStatus.COMPUTED);
        s.setTotals("{\"totalPaid\":\"" + totalPaid + "\"}");
        when(settlementRepository.findById(id)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    @DisplayName("high-value MATCH → released and marked biometric-stepped-up")
    void highValueMatchMarksBiometric() {
        computed("S-HV", "8000.00"); // above default threshold 5000
        when(biometricVerification.verify(eq("subj-approver-1"), eq("FINGERPRINT"), any()))
                .thenReturn(new BiometricVerificationClient.Decision("MATCH", 0.99));

        SettlementEntity released = service.releasePayouts("S-HV",
                "subj-approver-1", "FINGERPRINT", "cHJvYmU=");

        assertEquals(SettlementStatus.RELEASED, released.getStatus());
        assertEquals("biometric", released.getReleaseStepUpMode());
    }

    @Test
    @DisplayName("high-value NO_MATCH → release blocked, nothing released")
    void highValueNoMatchBlocks() {
        computed("S-BLK", "8000.00");
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(new BiometricVerificationClient.Decision("NO_MATCH", 0.05));

        assertThrows(IllegalStateException.class, () -> service.releasePayouts("S-BLK",
                "subj-approver-1", "FINGERPRINT", "cHJvYmU="));

        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("high-value UNAVAILABLE → falls back to non-biometric step-up (released, mode null)")
    void highValueUnavailableFallsBack() {
        SettlementEntity s = computed("S-OUT", "8000.00");
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(BiometricVerificationClient.Decision.unavailable());

        SettlementEntity released = service.releasePayouts("S-OUT",
                "subj-approver-1", "FINGERPRINT", "cHJvYmU=");

        assertEquals(SettlementStatus.RELEASED, released.getStatus());
        assertNull(released.getReleaseStepUpMode(), "outage must not fabricate a biometric step-up");
    }

    @Test
    @DisplayName("below threshold with probe → biometric not required, verify never called")
    void belowThresholdSkipsBiometric() {
        computed("S-LOW", "100.00"); // below default threshold 5000
        SettlementEntity released = service.releasePayouts("S-LOW",
                "subj-approver-1", "FINGERPRINT", "cHJvYmU=");

        assertEquals(SettlementStatus.RELEASED, released.getStatus());
        assertNull(released.getReleaseStepUpMode());
        verify(biometricVerification, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("no probe supplied → unchanged release, verify never called")
    void noProbeUnchanged() {
        computed("S-NONE", "8000.00");
        SettlementEntity released = service.releasePayouts("S-NONE");

        assertEquals(SettlementStatus.RELEASED, released.getStatus());
        assertNull(released.getReleaseStepUpMode());
        verify(biometricVerification, never()).verify(any(), any(), any());
    }
}
