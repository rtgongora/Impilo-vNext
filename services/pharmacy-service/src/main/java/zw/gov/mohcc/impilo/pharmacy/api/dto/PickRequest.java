package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for picking items from shelves during the dispense workflow.
 *
 * @param items the list of items to pick with batch and quantity details
 */
public record PickRequest(
        @NotEmpty @Valid List<PickItemData> items
) {}
