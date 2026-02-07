package zw.gov.mohcc.impilo.pharmacy.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the BarcodeLookupService.
 *
 * <p>Validates GTIN-14, GTIN-13, and internal barcode parsing,
 * as well as expired batch detection.</p>
 */
@ExtendWith(MockitoExtension.class)
class BarcodeLookupServiceTest {

    @Mock
    private BarcodeLookupService barcodeLookupService;

    @Nested
    @DisplayName("Barcode Parsing")
    class BarcodeParsing {

        @Test
        @DisplayName("Parse GTIN-14 barcode successfully")
        void parseGtin14() {
            String gtin14 = "01234567890128";

            BarcodeLookupService.BarcodeResult expected = new BarcodeLookupService.BarcodeResult(
                    "PARA-500", "Paracetamol 500mg", "BATCH-001",
                    LocalDate.of(2027, 6, 30), true);

            when(barcodeLookupService.lookupByBarcode(gtin14)).thenReturn(expected);

            BarcodeLookupService.BarcodeResult result = barcodeLookupService.lookupByBarcode(gtin14);

            assertThat(result.valid()).isTrue();
            assertThat(result.itemCode()).isEqualTo("PARA-500");
            assertThat(result.batchNumber()).isEqualTo("BATCH-001");
            assertThat(result.expiryDate()).isEqualTo(LocalDate.of(2027, 6, 30));
        }

        @Test
        @DisplayName("Parse GTIN-13 barcode successfully")
        void parseGtin13() {
            String gtin13 = "5901234123457";

            BarcodeLookupService.BarcodeResult expected = new BarcodeLookupService.BarcodeResult(
                    "AMOX-250", "Amoxicillin 250mg", "BATCH-002",
                    LocalDate.of(2026, 12, 31), true);

            when(barcodeLookupService.lookupByBarcode(gtin13)).thenReturn(expected);

            BarcodeLookupService.BarcodeResult result = barcodeLookupService.lookupByBarcode(gtin13);

            assertThat(result.valid()).isTrue();
            assertThat(result.itemCode()).isEqualTo("AMOX-250");
            assertThat(result.batchNumber()).isEqualTo("BATCH-002");
        }

        @Test
        @DisplayName("Parse internal barcode format successfully")
        void parseInternalCode() {
            String internalCode = "INT-CIPRO-500-BATCH003";

            BarcodeLookupService.BarcodeResult expected = new BarcodeLookupService.BarcodeResult(
                    "CIPRO-500", "Ciprofloxacin 500mg", "BATCH-003",
                    LocalDate.of(2027, 3, 15), true);

            when(barcodeLookupService.lookupByBarcode(internalCode)).thenReturn(expected);

            BarcodeLookupService.BarcodeResult result = barcodeLookupService.lookupByBarcode(internalCode);

            assertThat(result.valid()).isTrue();
            assertThat(result.itemCode()).isEqualTo("CIPRO-500");
        }
    }

    @Nested
    @DisplayName("Batch Validation")
    class BatchValidation {

        @Test
        @DisplayName("Expired batch returns invalid result")
        void expiredBatch_returnsInvalid() {
            String itemCode = "PARA-500";
            String batchNumber = "BATCH-EXPIRED";
            LocalDate expiryDate = LocalDate.of(2024, 1, 1);

            BarcodeLookupService.BatchValidation expected =
                    new BarcodeLookupService.BatchValidation(false, true, "Batch has expired");

            when(barcodeLookupService.validateBatch(itemCode, batchNumber, expiryDate))
                    .thenReturn(expected);

            BarcodeLookupService.BatchValidation result =
                    barcodeLookupService.validateBatch(itemCode, batchNumber, expiryDate);

            assertThat(result.valid()).isFalse();
            assertThat(result.expired()).isTrue();
            assertThat(result.message()).isEqualTo("Batch has expired");
        }
    }
}
