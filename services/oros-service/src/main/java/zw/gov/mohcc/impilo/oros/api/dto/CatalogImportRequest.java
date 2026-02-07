package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for bulk catalog import.
 */
public record CatalogImportRequest(
        @NotEmpty @Valid List<CatalogItemDto> items
) {}
