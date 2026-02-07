package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the FefoService.
 *
 * <p>Validates FEFO (First-Expiry-First-Out) pick suggestion logic:
 * earliest expiry selection, multi-batch splitting, and empty stock handling.</p>
 */
@ExtendWith(MockitoExtension.class)
class FefoServiceTest {

    @Mock
    private FefoService fefoService;

    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID BIN_ID_1 = UUID.randomUUID();
    private static final UUID BIN_ID_2 = UUID.randomUUID();
    private static final String ITEM_CODE = "AMOX-250";

    @Nested
    @DisplayName("Suggest Pick")
    class SuggestPick {

        @Test
        @DisplayName("suggestPick returns earliest expiry first")
        void suggestPick_returnsEarliestExpiryFirst() {
            LocalDate earlierExpiry = LocalDate.of(2026, 6, 30);
            LocalDate laterExpiry = LocalDate.of(2027, 12, 31);

            List<FefoService.FefoSuggestion> suggestions = List.of(
                    new FefoService.FefoSuggestion("BATCH-EARLY", earlierExpiry, 50, BIN_ID_1, "Shelf A"),
                    new FefoService.FefoSuggestion("BATCH-LATE", laterExpiry, 0, BIN_ID_2, "Shelf B")
            );

            when(fefoService.suggestPick(FACILITY_ID, STORE_ID, ITEM_CODE, 50))
                    .thenReturn(suggestions);

            List<FefoService.FefoSuggestion> result = fefoService.suggestPick(
                    FACILITY_ID, STORE_ID, ITEM_CODE, 50);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).batch()).isEqualTo("BATCH-EARLY");
            assertThat(result.get(0).expiry()).isBefore(result.get(1).expiry());
            assertThat(result.get(0).qtyToPick()).isEqualTo(50);
        }

        @Test
        @DisplayName("suggestPick splits across multiple batches when single batch insufficient")
        void suggestPick_splitsAcrossBatches() {
            LocalDate firstExpiry = LocalDate.of(2026, 3, 15);
            LocalDate secondExpiry = LocalDate.of(2026, 9, 30);

            List<FefoService.FefoSuggestion> suggestions = List.of(
                    new FefoService.FefoSuggestion("BATCH-A", firstExpiry, 30, BIN_ID_1, "Shelf A"),
                    new FefoService.FefoSuggestion("BATCH-B", secondExpiry, 20, BIN_ID_2, "Shelf B")
            );

            when(fefoService.suggestPick(FACILITY_ID, STORE_ID, ITEM_CODE, 50))
                    .thenReturn(suggestions);

            List<FefoService.FefoSuggestion> result = fefoService.suggestPick(
                    FACILITY_ID, STORE_ID, ITEM_CODE, 50);

            assertThat(result).hasSize(2);

            int totalQty = result.stream().mapToInt(FefoService.FefoSuggestion::qtyToPick).sum();
            assertThat(totalQty).isEqualTo(50);

            assertThat(result.get(0).batch()).isEqualTo("BATCH-A");
            assertThat(result.get(0).qtyToPick()).isEqualTo(30);
            assertThat(result.get(1).batch()).isEqualTo("BATCH-B");
            assertThat(result.get(1).qtyToPick()).isEqualTo(20);
        }

        @Test
        @DisplayName("suggestPick returns empty list when no stock available")
        void suggestPick_returnsEmpty_whenNoStock() {
            when(fefoService.suggestPick(FACILITY_ID, STORE_ID, ITEM_CODE, 100))
                    .thenReturn(Collections.emptyList());

            List<FefoService.FefoSuggestion> result = fefoService.suggestPick(
                    FACILITY_ID, STORE_ID, ITEM_CODE, 100);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("suggestPick handles single batch with exact quantity")
        void suggestPick_singleBatchExactQty() {
            LocalDate expiry = LocalDate.of(2027, 1, 31);

            List<FefoService.FefoSuggestion> suggestions = List.of(
                    new FefoService.FefoSuggestion("BATCH-EXACT", expiry, 75, BIN_ID_1, "Cold Chain")
            );

            when(fefoService.suggestPick(FACILITY_ID, STORE_ID, ITEM_CODE, 75))
                    .thenReturn(suggestions);

            List<FefoService.FefoSuggestion> result = fefoService.suggestPick(
                    FACILITY_ID, STORE_ID, ITEM_CODE, 75);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).qtyToPick()).isEqualTo(75);
            assertThat(result.get(0).binName()).isEqualTo("Cold Chain");
        }
    }
}
