package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.core.FefoService;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO representing a single FEFO pick suggestion.
 *
 * @param batch     the batch to pick from
 * @param expiry    the batch expiry date
 * @param qtyToPick the quantity to pick from this batch
 * @param binId     the bin containing this batch
 * @param binName   the display name of the bin
 */
public record FefoSuggestionDto(
        String batch,
        LocalDate expiry,
        int qtyToPick,
        UUID binId,
        String binName
) {

    /**
     * Map a FefoSuggestion from the core service to a DTO.
     *
     * @param suggestion the core service FEFO suggestion
     * @return the DTO representation
     */
    public static FefoSuggestionDto from(FefoService.FefoSuggestion suggestion) {
        return new FefoSuggestionDto(
                suggestion.batch(),
                suggestion.expiry(),
                suggestion.qtyToPick(),
                suggestion.binId(),
                suggestion.binName()
        );
    }
}
