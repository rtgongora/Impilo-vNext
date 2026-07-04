package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.LedgerEventRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.OnHandRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the real {@link LedgerServiceImpl} posting path, proving the low-stock
 * telemetry gap is closed: every posted movement emits an
 * {@code INVENTORY_STOCK_LEVEL_TELEMETRY} outbox row alongside
 * {@code LEDGER_EVENT_CREATED}, and outflows that exhaust usable stock emit
 * {@code STOCKOUT_RISK}. Reads and idempotent duplicates emit nothing.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTelemetryTest {

    @Mock
    private LedgerEventRepository ledgerEventRepository;

    @Mock
    private OnHandRepository onHandRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private LedgerServiceImpl ledgerService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID STORE_ID = UUID.randomUUID();
    private static final String ITEM_CODE = "PARA-500";
    private static final String BATCH = "BATCH-001";
    private static final LocalDate EXPIRY = LocalDate.of(2027, 6, 30);

    @BeforeEach
    void setUp() {
        // findAndRegisterModules: match Spring's mapper (java.time support) so
        // LedgerEventEntity payload serialization behaves like production.
        ledgerService = new LedgerServiceImpl(
                ledgerEventRepository, onHandRepository, outboxRepository,
                new ObjectMapper().findAndRegisterModules());
        TrustContextHolder.set(new TrustContext(
                TENANT_ID, "test-actor", "SYSTEM", "TESTING",
                null, UUID.randomUUID(), FACILITY_ID, null, null, null));

        // Persisted ledger events get a generated id; simulate it so outbox
        // payload construction (which reads eventId) behaves like production.
        // Lenient: the idempotent-duplicate test never reaches save().
        org.mockito.Mockito.lenient().when(ledgerEventRepository.save(any(LedgerEventEntity.class))).thenAnswer(inv -> {
            LedgerEventEntity e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "eventId", UUID.randomUUID());
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private LedgerService.LedgerEventData movement(int qtyDelta, LedgerEventType type, String idemKey) {
        return new LedgerService.LedgerEventData(
                FACILITY_ID, STORE_ID, null, ITEM_CODE, BATCH, EXPIRY,
                qtyDelta, "TAB", type, "test", "TEST", "TEST-1", idemKey);
    }

    private OnHandEntity onHand(int qty) {
        OnHandEntity e = new OnHandEntity();
        e.setTenantId(TENANT_ID);
        e.setFacilityId(FACILITY_ID);
        e.setStoreId(STORE_ID);
        e.setItemCode(ITEM_CODE);
        e.setBatch(BATCH);
        e.setExpiry(EXPIRY);
        e.setQtyOnHand(qty);
        return e;
    }

    private List<String> capturedOutboxEventTypes() {
        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(EventOutboxEntity::getEventType).toList();
    }

    @Test
    @DisplayName("receipt emits ledger + stock-level telemetry outbox rows, no stockout risk")
    void receipt_emitsTelemetry_noStockoutRisk() {
        when(onHandRepository.findByFacilityIdAndStoreIdAndItemCodeAndBatchAndExpiry(
                FACILITY_ID, STORE_ID, ITEM_CODE, BATCH, EXPIRY))
                .thenReturn(Optional.empty());

        ledgerService.postEvent(movement(100, LedgerEventType.RECEIPT, "IDEM-R1"));

        List<String> types = capturedOutboxEventTypes();
        assertThat(types).containsExactly("LEDGER_EVENT_CREATED", "INVENTORY_STOCK_LEVEL_TELEMETRY");
        assertThat(types).doesNotContain("STOCKOUT_RISK");
    }

    @Test
    @DisplayName("issue exhausting stock emits telemetry AND stockout risk")
    void issueToZero_emitsStockoutRisk() {
        OnHandEntity existing = onHand(30);
        when(onHandRepository.findByFacilityIdAndStoreIdAndItemCodeAndBatchAndExpiry(
                FACILITY_ID, STORE_ID, ITEM_CODE, BATCH, EXPIRY))
                .thenReturn(Optional.of(existing));
        // stockout evaluation sums across all batches for the item at the store
        when(onHandRepository.findByFacilityIdAndStoreIdAndItemCode(FACILITY_ID, STORE_ID, ITEM_CODE))
                .thenReturn(List.of(existing));

        ledgerService.postEvent(movement(-30, LedgerEventType.ISSUE, "IDEM-I1"));

        assertThat(existing.getQtyOnHand()).isZero();
        List<String> types = capturedOutboxEventTypes();
        assertThat(types).containsExactly(
                "LEDGER_EVENT_CREATED", "INVENTORY_STOCK_LEVEL_TELEMETRY", "STOCKOUT_RISK");
    }

    @Test
    @DisplayName("issue leaving positive stock emits telemetry but no stockout risk")
    void issueLeavingStock_noStockoutRisk() {
        OnHandEntity existing = onHand(30);
        when(onHandRepository.findByFacilityIdAndStoreIdAndItemCodeAndBatchAndExpiry(
                FACILITY_ID, STORE_ID, ITEM_CODE, BATCH, EXPIRY))
                .thenReturn(Optional.of(existing));
        when(onHandRepository.findByFacilityIdAndStoreIdAndItemCode(FACILITY_ID, STORE_ID, ITEM_CODE))
                .thenReturn(List.of(existing));

        ledgerService.postEvent(movement(-10, LedgerEventType.ISSUE, "IDEM-I2"));

        assertThat(existing.getQtyOnHand()).isEqualTo(20);
        List<String> types = capturedOutboxEventTypes();
        assertThat(types).containsExactly("LEDGER_EVENT_CREATED", "INVENTORY_STOCK_LEVEL_TELEMETRY");
    }

    @Test
    @DisplayName("stockout risk considers other batches with remaining stock")
    void otherBatchWithStock_suppressesStockoutRisk() {
        OnHandEntity drained = onHand(10);
        OnHandEntity otherBatch = onHand(50);
        otherBatch.setBatch("BATCH-002");
        when(onHandRepository.findByFacilityIdAndStoreIdAndItemCodeAndBatchAndExpiry(
                FACILITY_ID, STORE_ID, ITEM_CODE, BATCH, EXPIRY))
                .thenReturn(Optional.of(drained));
        when(onHandRepository.findByFacilityIdAndStoreIdAndItemCode(FACILITY_ID, STORE_ID, ITEM_CODE))
                .thenReturn(List.of(drained, otherBatch));

        ledgerService.postEvent(movement(-10, LedgerEventType.ISSUE, "IDEM-I3"));

        List<String> types = capturedOutboxEventTypes();
        assertThat(types).doesNotContain("STOCKOUT_RISK");
    }

    @Test
    @DisplayName("idempotent duplicate returns existing event and emits nothing")
    void idempotentDuplicate_emitsNothing() {
        LedgerEventEntity existing = new LedgerEventEntity();
        when(ledgerEventRepository.findByIdempotencyKey("IDEM-DUP"))
                .thenReturn(Optional.of(existing));

        LedgerEventEntity result = ledgerService.postEvent(movement(-5, LedgerEventType.ISSUE, "IDEM-DUP"));

        assertThat(result).isSameAs(existing);
        verify(outboxRepository, never()).save(any());
        verify(onHandRepository, never()).save(any());
    }
}
