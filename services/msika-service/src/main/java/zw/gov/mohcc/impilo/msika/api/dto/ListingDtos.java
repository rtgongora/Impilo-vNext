package zw.gov.mohcc.impilo.msika.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs for the buyer-facing storefront lane (listings + media). */
public final class ListingDtos {

    private ListingDtos() {}

    public record CreateListingRequest(
            UUID tenantId,
            String storefrontId,
            @NotBlank String catalogItemId,
            String offeringId,
            @NotBlank String sellerType,
            @NotBlank String sellerId,
            @NotBlank String title,
            String summary,
            String riskClassification,
            Object eligibilityRule,
            String clinicalRoute,
            String chargeRef,
            String priceDisplay,
            BigDecimal priceAmount,
            String priceCurrency,
            String fulfilmentMode,
            String fundoRef,
            Object metadata
    ) {}

    public record UpdateListingRequest(
            String title,
            String summary,
            String riskClassification,
            Object eligibilityRule,
            String clinicalRoute,
            String chargeRef,
            String priceDisplay,
            BigDecimal priceAmount,
            String priceCurrency,
            String fulfilmentMode,
            String fundoRef,
            Object metadata
    ) {}

    /** Moderation decision (approve / reject / request-correction / suspend / reinstate). */
    public record ModerationRequest(
            String notes
    ) {}

    public record MediaRequest(
            @NotBlank String mediaRef,
            String mediaType,
            String caption,
            Integer sortOrder
    ) {}

    public record MediaView(
            String mediaId,
            String listingId,
            String mediaType,
            String mediaRef,
            String caption,
            int sortOrder,
            OffsetDateTime createdAt
    ) {}

    public record ListingView(
            String listingId,
            UUID tenantId,
            String storefrontId,
            String catalogItemId,
            String offeringId,
            String sellerType,
            String sellerId,
            String title,
            String summary,
            String riskClassification,
            Object eligibilityRule,
            String clinicalRoute,
            String chargeRef,
            String priceDisplay,
            BigDecimal priceAmount,
            String priceCurrency,
            String fulfilmentMode,
            String fulfilmentOwner,
            String status,
            String moderationNotes,
            String approvedBy,
            OffsetDateTime approvedAt,
            OffsetDateTime publishedAt,
            String fundoRef,
            Object metadata,
            List<MediaView> media,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
