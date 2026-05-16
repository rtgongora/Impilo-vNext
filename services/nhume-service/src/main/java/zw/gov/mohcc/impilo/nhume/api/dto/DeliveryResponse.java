package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Compact response representation of a Nhume delivery — safe to expose to
 * dispatcher consoles and provider views. Detail endpoints can layer on
 * timeline, packages, items, proofs and custody events on top of this.
 */
public record DeliveryResponse(
        @JsonProperty("delivery_id") UUID deliveryId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("reference_code") String referenceCode,
        @JsonProperty("status") String status,
        @JsonProperty("priority") String priority,
        @JsonProperty("delivery_type") String deliveryType,
        @JsonProperty("request_source") String requestSource,
        @JsonProperty("origin_label") String originLabel,
        @JsonProperty("destination_label") String destinationLabel,
        @JsonProperty("recipient_name") String recipientName,
        @JsonProperty("recipient_phone") String recipientPhone,
        @JsonProperty("requesting_facility_id") String requestingFacilityId,
        @JsonProperty("telehealth_session_ref") String telehealthSessionRef,
        @JsonProperty("marketplace_order_ref") String marketplaceOrderRef,
        @JsonProperty("policy_id") String policyId,
        @JsonProperty("sla_id") String slaId,
        @JsonProperty("sla_due_at") OffsetDateTime slaDueAt,
        @JsonProperty("cold_chain_required") boolean coldChainRequired,
        @JsonProperty("controlled_item") boolean controlledItem,
        @JsonProperty("chain_of_custody_required") boolean chainOfCustodyRequired,
        @JsonProperty("is_demo") boolean demo,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("submitted_at") OffsetDateTime submittedAt,
        @JsonProperty("delivered_at") OffsetDateTime deliveredAt,
        @JsonProperty("failed_at") OffsetDateTime failedAt
) {}
