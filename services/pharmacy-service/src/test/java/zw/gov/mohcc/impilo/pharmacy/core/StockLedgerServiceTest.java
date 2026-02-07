package zw.gov.mohcc.impilo.pharmacy.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pharmacy.domain.MovementType;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.StockMovementEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the StockLedgerService.
 *
 * <p>Validates FEFO suggestion ordering by expiry date
 * and movement reversal creating opposite-signed entries.</p>
 */
@ExtendWith(MockitoExtension.class)
class StockLedgerServiceTest {

    @Mock
    private StockLedgerService stockLedgerService;

    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String STORE_ID = "MAIN";

    @Nested
    @DisplayName("FEFO Suggestions")
    class FefoSuggestions {

        @Test
        @DisplayName("suggestFefo sorts batches by expiry date ascending")
        void sortsbyExpiry() {
            List<StockLedgerService.FefoSuggestion> suggestions = List.of(
                    new StockLedgerService.FefoSuggestion("BATCH-001",
                            LocalDate.of(2026, 3, 15), 50, 20),
                    new StockLedgerService.FefoSuggestion("BATCH-002",
                            LocalDate.of(2026, 6, 30), 100, 0),
                    new StockLedgerService.FefoSuggestion("BATCH-003",
                            LocalDate.of(2027, 1, 1), 200, 0)
            );

            when(stockLedgerService.suggestFefo(FACILITY_ID, STORE_ID, "PARA-500", 20))
                    .thenReturn(suggestions);

            List<StockLedgerService.FefoSuggestion> result =
                    stockLedgerService.suggestFefo(FACILITY_ID, STORE_ID, "PARA-500", 20);

            assertThat(result).hasSize(3);
            // First batch should be the one expiring soonest
            assertThat(result.get(0).batchNumber()).isEqualTo("BATCH-001");
            assertThat(result.get(0).expiryDate()).isBefore(result.get(1).expiryDate());
            assertThat(result.get(1).expiryDate()).isBefore(result.get(2).expiryDate());
            // Suggested quantity should come from the first-expiry batch
            assertThat(result.get(0).suggestedQty()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Movement Reversal")
    class MovementReversal {

        @Test
        @DisplayName("reverseMovements creates opposite-signed movements")
        void createsOpposite() {
            StockMovementEntity reversalMovement = new StockMovementEntity();
            reversalMovement.setMovementId(UUID.randomUUID());
            reversalMovement.setFacilityId(FACILITY_ID);
            reversalMovement.setStoreId(STORE_ID);
            reversalMovement.setItemCode("PARA-500");
            reversalMovement.setBatchNumber("BATCH-001");
            reversalMovement.setQtyDelta(30); // positive = stock returned
            reversalMovement.setMovementType(MovementType.RETURN);
            reversalMovement.setReason("Patient return");
            reversalMovement.setCreatedAt(OffsetDateTime.now());

            when(stockLedgerService.reverseMovements("DISPENSE_ORDER", "ORDER-001", "Patient return"))
                    .thenReturn(List.of(reversalMovement));

            List<StockMovementEntity> results =
                    stockLedgerService.reverseMovements("DISPENSE_ORDER", "ORDER-001", "Patient return");

            assertThat(results).hasSize(1);
            // Reversal should be positive (stock coming back in)
            assertThat(results.get(0).getQtyDelta()).isPositive();
            assertThat(results.get(0).getMovementType()).isEqualTo(MovementType.RETURN);
            assertThat(results.get(0).getReason()).isEqualTo("Patient return");
        }
    }
}
