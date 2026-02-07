package zw.gov.mohcc.impilo.pharmacy.api.dto;

import java.time.LocalDate;

/**
 * DTO representing a FEFO (First-Expiry-First-Out) stock suggestion
 * for picking during the dispense workflow.
 *
 * @param batchNumber  the batch number to pick from
 * @param expiryDate   the expiry date of the batch
 * @param availableQty the total quantity available in this batch
 * @param suggestedQty the suggested quantity to pick from this batch
 */
public record FefoSuggestionDto(
        String batchNumber,
        LocalDate expiryDate,
        int availableQty,
        int suggestedQty
) {}
