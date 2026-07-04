package zw.gov.mohcc.impilo.inventory.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.core.ConsumptionPostingService;
import zw.gov.mohcc.impilo.inventory.domain.StoreType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StoreEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.StoreRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the {@code pharmacy.stock.movement.requested} Kafka consumption path
 * (the previously untested Dura↔pharmacy hook): payload parsing, synthetic
 * trust-context lifecycle, non-UUID store-code resolution to the facility
 * PHARMACY store, and skip semantics for malformed/incomplete events.
 */
@ExtendWith(MockitoExtension.class)
class PharmacyConsumerTest {

    @Mock
    private ConsumptionPostingService consumptionPostingService;

    @Mock
    private StoreRepository storeRepository;

    private PharmacyConsumer consumer;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID STORE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new PharmacyConsumer(consumptionPostingService, new ObjectMapper(), storeRepository);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private String event(String storeId) {
        return """
                {
                  "tenantId": "%s",
                  "facilityId": "%s",
                  "storeId": "%s",
                  "itemCode": "PARA-500",
                  "batch": "BATCH-001",
                  "expiry": "2027-06-30",
                  "qty": 30,
                  "orderId": "OROS-ORDER-001",
                  "actorId": "pharmacist-001"
                }
                """.formatted(TENANT_ID, FACILITY_ID, storeId);
    }

    @Test
    @DisplayName("valid event with UUID store posts pharmacy consumption to the ledger")
    void validEvent_postsConsumption() {
        consumer.consumePharmacyMovement(event(STORE_ID.toString()));

        verify(consumptionPostingService).postPharmacyConsumption(
                FACILITY_ID, STORE_ID, "PARA-500", "BATCH-001",
                LocalDate.of(2027, 6, 30), 30, "OROS-ORDER-001");
        // synthetic trust context must not leak onto the consumer thread
        assertThat(TrustContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("non-UUID pharmacy store code resolves to the facility PHARMACY store")
    void localStoreCode_resolvesToPharmacyStore() {
        StoreEntity pharmacyStore = new StoreEntity();
        pharmacyStore.setStoreId(STORE_ID);
        pharmacyStore.setFacilityId(FACILITY_ID);
        pharmacyStore.setStoreType(StoreType.PHARMACY);
        when(storeRepository.findByFacilityIdAndStoreType(FACILITY_ID, StoreType.PHARMACY))
                .thenReturn(List.of(pharmacyStore));

        consumer.consumePharmacyMovement(event("DEFAULT"));

        verify(consumptionPostingService).postPharmacyConsumption(
                FACILITY_ID, STORE_ID, "PARA-500", "BATCH-001",
                LocalDate.of(2027, 6, 30), 30, "OROS-ORDER-001");
    }

    @Test
    @DisplayName("non-UUID store code with no PHARMACY store skips posting")
    void localStoreCode_noPharmacyStore_skips() {
        when(storeRepository.findByFacilityIdAndStoreType(FACILITY_ID, StoreType.PHARMACY))
                .thenReturn(List.of());

        consumer.consumePharmacyMovement(event("DEFAULT"));

        verify(consumptionPostingService, never()).postPharmacyConsumption(
                any(), any(), anyString(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("event missing required fields is skipped without posting")
    void missingRequiredFields_skips() {
        consumer.consumePharmacyMovement("""
                {"tenantId": "%s", "qty": 10}
                """.formatted(TENANT_ID));

        verifyNoInteractions(consumptionPostingService);
    }

    @Test
    @DisplayName("non-positive quantity is skipped without posting")
    void nonPositiveQty_skips() {
        String payload = event(STORE_ID.toString()).replace("\"qty\": 30", "\"qty\": 0");

        consumer.consumePharmacyMovement(payload);

        verifyNoInteractions(consumptionPostingService);
    }

    @Test
    @DisplayName("malformed JSON is swallowed (no rethrow, no posting)")
    void malformedJson_doesNotThrow() {
        consumer.consumePharmacyMovement("{not json");

        verifyNoInteractions(consumptionPostingService);
    }

    @Test
    @DisplayName("posting failure does not propagate to the Kafka container")
    void postingFailure_doesNotPropagate() {
        when(consumptionPostingService.postPharmacyConsumption(
                any(), any(), anyString(), any(), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("ledger down"));

        consumer.consumePharmacyMovement(event(STORE_ID.toString()));

        assertThat(TrustContextHolder.get()).isNull();
    }
}
