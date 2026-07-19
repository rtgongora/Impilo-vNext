package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PayoutBatchEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PayoutItemEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.SettlementEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.PayeeType;
import zw.gov.mohcc.impilo.mushex.domain.enums.PayoutStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SettlementStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.LedgerEntryRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PayoutBatchRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PayoutItemRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.SettlementRepository;
import zw.gov.mohcc.impilo.mushex.integration.MusheWalletAdapter;
import zw.gov.mohcc.impilo.mushex.service.disbursement.DisbursementRailRegistry;
import zw.gov.mohcc.impilo.mushex.service.disbursement.WalletDisbursementRail;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tier-1 payout truth: settlements aggregate by PAYEE using the payee's SHARE
 * (never the gross that includes the platform fee); vendor batches ride the
 * internal WALLET rail and actually disburse on release; batches with no
 * registered rail stay PENDING (fail-closed); partial disbursement is PARTIAL,
 * and the ledger reflects only what was actually credited.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementPayoutTest {

    @Mock SettlementRepository settlementRepository;
    @Mock PayoutBatchRepository batchRepository;
    @Mock PayoutItemRepository itemRepository;
    @Mock PaymentIntentRepository intentRepository;
    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock LedgerService ledgerService;
    @Mock EventOutboxRepository outboxRepository;
    @Mock MusheWalletAdapter walletAdapter;

    private final UUID tenantId = UUID.randomUUID();
    private SettlementService service;
    private List<PayoutBatchEntity> savedBatches;
    private List<PayoutItemEntity> savedItems;

    @BeforeEach
    void setUp() {
        savedBatches = new ArrayList<>();
        savedItems = new ArrayList<>();
        when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(batchRepository.save(any())).thenAnswer(inv -> {
            PayoutBatchEntity b = inv.getArgument(0);
            if (!savedBatches.contains(b)) savedBatches.add(b);
            return b;
        });
        when(itemRepository.save(any())).thenAnswer(inv -> {
            PayoutItemEntity i = inv.getArgument(0);
            if (!savedItems.contains(i)) savedItems.add(i);
            return i;
        });
        DisbursementRailRegistry registry =
                new DisbursementRailRegistry(List.of(new WalletDisbursementRail(walletAdapter)));
        service = new SettlementService(settlementRepository, batchRepository, itemRepository,
                intentRepository, ledgerEntryRepository, ledgerService, outboxRepository,
                new ObjectMapper(), registry, new zw.gov.mohcc.impilo.mushex.config.MushexProperties(),
                mock(zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient.class));
        TrustContextHolder.set(new TrustContext(
                tenantId, "ops-1", "SYSTEM_ADMIN", "FINANCE",
                "device-1", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private PaymentIntentEntity paidIntent(String id, String metadata, UUID facilityId, String paid) {
        PaymentIntentEntity i = new PaymentIntentEntity();
        i.setIntentId(id);
        i.setTenantId(tenantId);
        i.setSourceType(SourceType.MSIKA_ORDER);
        i.setSourceId("ORD-" + id);
        i.setCurrency("USD");
        i.setStatus(IntentStatus.PAID);
        i.setAmountTotal(new BigDecimal(paid));
        i.setAmountPaid(new BigDecimal(paid));
        i.setMetadata(metadata);
        i.setFacilityId(facilityId);
        return i;
    }

    @Test
    void runSettlement_aggregatesByPayee_usingPayeeShare_notGross() {
        UUID facility = UUID.randomUUID();
        when(intentRepository.findByTenantIdAndStatusAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of(
                        // vendor order: gross 43.14, vendor share 42.50 (fee excluded)
                        paidIntent("I1", "{\"provider_id\":\"vendor:VEND-1\",\"payee_amount\":\"42.50\"}", null, "43.14"),
                        // second order for the SAME vendor
                        paidIntent("I2", "{\"provider_id\":\"vendor:VEND-1\",\"payee_amount\":\"10.00\"}", null, "10.20"),
                        // facility intent (fees): gross goes to the facility payee
                        paidIntent("I3", "{}", facility, "120.00"),
                        // no payee resolvable — must be SKIPPED, not attributed to null
                        paidIntent("I4", "{}", null, "5.00")));

        service.runSettlement(tenantId, LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(2, savedBatches.size(), "one WALLET batch (vendor) + one BANK batch (facility)");
        PayoutBatchEntity walletBatch = savedBatches.stream()
                .filter(b -> b.getAdapterType() == AdapterType.WALLET).findFirst().orElseThrow();
        PayoutBatchEntity bankBatch = savedBatches.stream()
                .filter(b -> b.getAdapterType() == AdapterType.BANK_TRANSFER).findFirst().orElseThrow();

        PayoutItemEntity vendorItem = savedItems.stream()
                .filter(i -> i.getBatchId().equals(walletBatch.getBatchId())).findFirst().orElseThrow();
        assertEquals(PayeeType.VENDOR, vendorItem.getPayeeType());
        assertEquals("VEND-1", vendorItem.getPayeeRef());
        assertEquals(new BigDecimal("52.50"), vendorItem.getAmount(),
                "vendor gets SHARE (42.50+10.00), never gross incl. platform fee");

        PayoutItemEntity facilityItem = savedItems.stream()
                .filter(i -> i.getBatchId().equals(bankBatch.getBatchId())).findFirst().orElseThrow();
        assertEquals(PayeeType.FACILITY, facilityItem.getPayeeType());
        assertEquals(new BigDecimal("120.00"), facilityItem.getAmount());

        long totalItems = savedItems.size();
        assertEquals(2, totalItems, "no-payee intent skipped, never a 'null' payee item");
    }

    private SettlementEntity computedSettlement(String id) {
        SettlementEntity s = new SettlementEntity();
        s.setSettlementId(id);
        s.setTenantId(tenantId);
        s.setStatus(SettlementStatus.COMPUTED);
        when(settlementRepository.findById(id)).thenReturn(Optional.of(s));
        return s;
    }

    private PayoutBatchEntity batch(String id, String settlementId, AdapterType rail) {
        PayoutBatchEntity b = new PayoutBatchEntity();
        b.setBatchId(id);
        b.setSettlementId(settlementId);
        b.setAdapterType(rail);
        b.setStatus(PayoutStatus.PENDING);
        return b;
    }

    private PayoutItemEntity item(String id, String batchId, String payeeRef, String amount) {
        PayoutItemEntity i = new PayoutItemEntity();
        i.setId(id);
        i.setBatchId(batchId);
        i.setPayeeType(PayeeType.VENDOR);
        i.setPayeeRef(payeeRef);
        i.setAmount(new BigDecimal(amount));
        i.setCurrency("USD");
        i.setStatus(PayoutStatus.PENDING);
        return i;
    }

    @Test
    void releasePayouts_walletBatch_disburses_andLedgerReflectsActual() {
        computedSettlement("S1");
        PayoutBatchEntity b = batch("B1", "S1", AdapterType.WALLET);
        when(batchRepository.findBySettlementId("S1")).thenReturn(List.of(b));
        when(itemRepository.findByBatchId("B1"))
                .thenReturn(List.of(item("IT1", "B1", "VEND-1", "52.50")));
        when(walletAdapter.disburseToBatch(any(), any())).thenReturn(
                new MusheWalletAdapter.DisbursementResult(1, List.of()));

        service.releasePayouts("S1");

        assertEquals(PayoutStatus.COMPLETED, b.getStatus());
        verify(ledgerService).postSettlement(eq(tenantId), eq("S1"),
                eq(new BigDecimal("52.50")), eq("USD"));
    }

    @Test
    void releasePayouts_partialFailure_isPartial_andLedgerOnlyCreditedAmount() {
        computedSettlement("S2");
        PayoutBatchEntity b = batch("B2", "S2", AdapterType.WALLET);
        when(batchRepository.findBySettlementId("S2")).thenReturn(List.of(b));
        PayoutItemEntity ok = item("IT-OK", "B2", "VEND-1", "40.00");
        PayoutItemEntity failed = item("IT-BAD", "B2", "VEND-MISSING", "10.00");
        when(itemRepository.findByBatchId("B2")).thenReturn(List.of(ok, failed));
        when(walletAdapter.disburseToBatch(any(), any())).thenReturn(
                new MusheWalletAdapter.DisbursementResult(1, List.of(
                        new MusheWalletAdapter.FailedDisbursement("VEND-MISSING",
                                new BigDecimal("10.00"), "payout:B2:IT-BAD", "no merchant wallet"))));

        service.releasePayouts("S2");

        assertEquals(PayoutStatus.PARTIAL, b.getStatus());
        assertEquals(PayoutStatus.COMPLETED, ok.getStatus());
        assertEquals(PayoutStatus.FAILED, failed.getStatus());
        verify(ledgerService).postSettlement(eq(tenantId), eq("S2"),
                eq(new BigDecimal("40.00")), eq("USD"));
    }

    @Test
    void releasePayouts_bankBatchWithNoRail_staysPending_failClosed() {
        computedSettlement("S3");
        PayoutBatchEntity b = batch("B3", "S3", AdapterType.BANK_TRANSFER);
        when(batchRepository.findBySettlementId("S3")).thenReturn(List.of(b));
        when(itemRepository.findByBatchId("B3"))
                .thenReturn(List.of(item("IT3", "B3", "FAC-1", "120.00")));

        service.releasePayouts("S3");

        assertEquals(PayoutStatus.PENDING, b.getStatus(),
                "no registered external rail — batch must stay PENDING, never fake-completed");
        verify(walletAdapter, never()).disburseToBatch(any(), any());
        verify(ledgerService, never()).postSettlement(any(), any(), any(), any());
    }
}
