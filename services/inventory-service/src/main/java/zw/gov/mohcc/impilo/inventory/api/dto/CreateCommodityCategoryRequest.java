package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request to create a Dura commodity category.
 */
public record CreateCommodityCategoryRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        UUID parentCategoryId,
        @Size(max = 100) String programmeArea,
        @Size(max = 500) String description
) {}
