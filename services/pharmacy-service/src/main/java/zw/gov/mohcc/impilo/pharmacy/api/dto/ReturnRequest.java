package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for processing a patient return of dispensed items.
 *
 * @param items the list of items being returned
 */
public record ReturnRequest(
        @NotEmpty @Valid List<ReturnItemData> items
) {}
