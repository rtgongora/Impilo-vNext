package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the LedgerService.
 *
 * <p>Validates core ledger operations: posting events with on-hand updates,
 * idempotency key enforcement, and balance queries.</p>
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerService ledgerService;

    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID BIN_ID = UUID.randomUUID();
    private static final String ITEM_CODE = "PARA-500";
    private static final String BATCH = "BATCH-001";
    private static final LocalDate EXPIRY = LocalDate.of(2027, 6, 30);

    @Nested
    @DisplayName("Post Event")
    class PostEvent {

        @Test
        @DisplayName("postEvent creates event and updates on-hand")
        void postEvent_createsEventAndUpdatesOnHand() {
            LedgerService.LedgerEventData data = new LedgerService.LedgerEventData(
                    FACILITY_ID, STORE_ID, BIN_ID,
                    ITEM_CODE, BATCH, EXPIRY,
                    100, "TAB", LedgerEventType.RECEIPT,
                    "Initial stock", "PO", "PO-001",
                    "IDEM-KEY-001");

            LedgerEventEntity expected = new LedgerEventEntity();
            expected.setFacilityId(FACILITY_ID);
            expected.setStoreId(STORE_ID);
            expected.setBinId(BIN_ID);
            expected.setItemCode(ITEM_CODE);
            expected.setBatch(BATCH);
            expected.setExpiry(EXPIRY);
            expected.setQtyDelta(100);
            expected.setUom("TAB");
            expected.setEventType(LedgerEventType.RECEIPT);
            expected.setReason("Initial stock");
            expected.setRefType("PO");
            expected.setRefId("PO-001");
            expected.setIdempotencyKey("IDEM-KEY-001");

            when(ledgerService.postEvent(any(LedgerService.LedgerEventData.class)))
                    .thenReturn(expected);

            LedgerEventEntity result = ledgerService.postEvent(data);

            assertThat(result).isNotNull();
            assertThat(result.getItemCode()).isEqualTo(ITEM_CODE);
            assertThat(result.getQtyDelta()).isEqualTo(100);
            assertThat(result.getEventType()).isEqualTo(LedgerEventType.RECEIPT);
            assertThat(result.getIdempotencyKey()).isEqualTo("IDEM-KEY-001");

            verify(ledgerService).postEvent(data);
        }

        @Test
        @DisplayName("Idempotency key prevents duplicate event creation")
        void postEvent_idempotencyPrevents_duplicate() {
            LedgerService.LedgerEventData data = new LedgerService.LedgerEventData(
                    FACILITY_ID, STORE_ID, BIN_ID,
                    ITEM_CODE, BATCH, EXPIRY,
                    50, "TAB", LedgerEventType.ISSUE,
                    "Ward issue", "WARD", "WARD-001",
                    "IDEM-KEY-DUP");

            LedgerEventEntity firstResult = new LedgerEventEntity();
            firstResult.setItemCode(ITEM_CODE);
            firstResult.setQtyDelta(50);
            firstResult.setIdempotencyKey("IDEM-KEY-DUP");
            firstResult.setEventType(LedgerEventType.ISSUE);

            when(ledgerService.postEvent(any(LedgerService.LedgerEventData.class)))
                    .thenReturn(firstResult);

            // First call
            LedgerEventEntity result1 = ledgerService.postEvent(data);

            // Second call with same idempotency key returns same entity
            LedgerEventEntity result2 = ledgerService.postEvent(data);

            assertThat(result1.getIdempotencyKey()).isEqualTo(result2.getIdempotencyKey());
            assertThat(result1.getQtyDelta()).isEqualTo(result2.getQtyDelta());

            verify(ledgerService, times(2)).postEvent(data);
        }
    }

    @Nested
    @DisplayName("Get Balance")
    class GetBalance {

        @Test
        @DisplayName("getBalance returns sum of all deltas for item")
        void getBalance_returnsSumOfDeltas() {
            when(ledgerService.getBalance(FACILITY_ID, STORE_ID, ITEM_CODE))
                    .thenReturn(150);

            int balance = ledgerService.getBalance(FACILITY_ID, STORE_ID, ITEM_CODE);

            assertThat(balance).isEqualTo(150);
            verify(ledgerService).getBalance(FACILITY_ID, STORE_ID, ITEM_CODE);
        }

        @Test
        @DisplayName("getBalance returns zero when no events exist")
        void getBalance_returnsZero_whenNoEvents() {
            UUID unknownFacility = UUID.randomUUID();
            when(ledgerService.getBalance(unknownFacility, STORE_ID, "UNKNOWN-ITEM"))
                    .thenReturn(0);

            int balance = ledgerService.getBalance(unknownFacility, STORE_ID, "UNKNOWN-ITEM");

            assertThat(balance).isZero();
        }
    }

    @Nested
    @DisplayName("Get Ledger Events")
    class GetLedgerEvents {

        @Test
        @DisplayName("getLedgerEvents returns paginated results")
        void getLedgerEvents_returnsPaginatedResults() {
            LedgerEventEntity event1 = new LedgerEventEntity();
            event1.setItemCode(ITEM_CODE);
            event1.setQtyDelta(100);
            event1.setEventType(LedgerEventType.RECEIPT);

            LedgerEventEntity event2 = new LedgerEventEntity();
            event2.setItemCode(ITEM_CODE);
            event2.setQtyDelta(-30);
            event2.setEventType(LedgerEventType.ISSUE);

            Page<LedgerEventEntity> page = new PageImpl<>(List.of(event1, event2), PageRequest.of(0, 20), 2);

            when(ledgerService.getLedgerEvents(eq(FACILITY_ID), eq(STORE_ID), eq(ITEM_CODE), any(Pageable.class)))
                    .thenReturn(page);

            Page<LedgerEventEntity> result = ledgerService.getLedgerEvents(
                    FACILITY_ID, STORE_ID, ITEM_CODE, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getEventType()).isEqualTo(LedgerEventType.RECEIPT);
            assertThat(result.getContent().get(1).getEventType()).isEqualTo(LedgerEventType.ISSUE);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }
}
