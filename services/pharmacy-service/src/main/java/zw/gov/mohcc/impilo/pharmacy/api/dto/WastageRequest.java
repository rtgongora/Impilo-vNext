package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for recording wastage of dispensed items.
 *
 * @param items the list of items being wasted
 */
public record WastageRequest(
        @NotEmpty @Valid List<WastageItemData> items
) {}
