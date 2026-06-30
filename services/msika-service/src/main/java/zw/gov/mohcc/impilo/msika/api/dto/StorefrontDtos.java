package zw.gov.mohcc.impilo.msika.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.UUID;

/** DTOs for seller storefronts. */
public final class StorefrontDtos {

    private StorefrontDtos() {}

    public record CreateStorefrontRequest(
            UUID tenantId,
            @NotBlank String sellerType,
            @NotBlank String sellerId,
            @NotBlank String displayName,
            String description,
            Object contactRefs,
            Object serviceArea,
            Object metadata
    ) {}

    /** Seller-identity validation outcome (validated upstream against Varapi/Tuso/Indawo). */
    public record VerifyStorefrontRequest(
            @NotBlank String verificationStatus,   // VERIFIED | REJECTED | SUSPENDED
            String notes
    ) {}

    public record StorefrontView(
            String storefrontId,
            UUID tenantId,
            String sellerType,
            String sellerId,
            String displayName,
            String description,
            String verificationStatus,
            String verifiedBy,
            OffsetDateTime verifiedAt,
            Object contactRefs,
            Object serviceArea,
            Object metadata,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
