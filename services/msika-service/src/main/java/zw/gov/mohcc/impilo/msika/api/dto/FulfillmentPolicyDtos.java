package zw.gov.mohcc.impilo.msika.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class FulfillmentPolicyDtos {

    private FulfillmentPolicyDtos() {}

    public record CreateFulfillmentPolicyRequest(
            UUID tenantId,
            String catalogItemId,
            String offeringId,
            Object allowedFulfillmentModes,
            Object identityRequirements,
            Object deliveryConstraints,
            Object substitutionPolicy,
            Object custodyRequirements,
            Object proofRequirements,
            String restrictionsSummary,
            Boolean active,
            Object metadata
    ) {}

    public record UpdateFulfillmentPolicyRequest(
            Object allowedFulfillmentModes,
            Object identityRequirements,
            Object deliveryConstraints,
            Object substitutionPolicy,
            Object custodyRequirements,
            Object proofRequirements,
            String restrictionsSummary,
            Boolean active,
            Object metadata
    ) {}

    public record FulfillmentPolicyView(
            String policyId,
            UUID tenantId,
            String catalogItemId,
            String offeringId,
            Object allowedFulfillmentModes,
            Object identityRequirements,
            Object deliveryConstraints,
            Object substitutionPolicy,
            Object custodyRequirements,
            Object proofRequirements,
            String restrictionsSummary,
            boolean active,
            Object metadata,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}

