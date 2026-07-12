package zw.gov.mohcc.impilo.msika.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class OfferingDtos {

    private OfferingDtos() {}

    public record CreateOfferingRequest(
            UUID tenantId,
            String catalogItemId,
            String offeringType,
            String providerType,
            String providerRef,
            UUID facilityRef,
            UUID vendorRef,
            Boolean active,
            String availabilityState,
            String inventoryMode,
            String bookingMode,
            Object pickupModesSupported,
            Object deliveryModesSupported,
            Integer leadTimeMinutes,
            Object geographicCoverage,
            Object metadata
    ) {}

    public record UpdateOfferingRequest(
            Boolean active,
            String availabilityState,
            String inventoryMode,
            String bookingMode,
            Object pickupModesSupported,
            Object deliveryModesSupported,
            Integer leadTimeMinutes,
            Object geographicCoverage,
            Object metadata
    ) {}

    /** Resolution result: the offering plus its effective fulfillment policy (nullable when none is configured). */
    public record ResolvedOfferingView(
            OfferingView offering,
            FulfillmentPolicyDtos.FulfillmentPolicyView fulfillmentPolicy
    ) {}

    public record OfferingView(
            String offeringId,
            UUID tenantId,
            String catalogItemId,
            String offeringType,
            String providerType,
            String providerRef,
            UUID facilityRef,
            UUID vendorRef,
            boolean active,
            String availabilityState,
            String inventoryMode,
            String bookingMode,
            Object pickupModesSupported,
            Object deliveryModesSupported,
            Integer leadTimeMinutes,
            Object geographicCoverage,
            Object metadata,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}

