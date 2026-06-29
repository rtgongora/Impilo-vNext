package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.CommodityCategoryEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for a Dura commodity category.
 */
public record CommodityCategoryDto(
        UUID categoryId,
        String code,
        String name,
        UUID parentCategoryId,
        String programmeArea,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CommodityCategoryDto from(CommodityCategoryEntity e) {
        return new CommodityCategoryDto(
                e.getCategoryId(),
                e.getCode(),
                e.getName(),
                e.getParentCategoryId(),
                e.getProgrammeArea(),
                e.getDescription(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
