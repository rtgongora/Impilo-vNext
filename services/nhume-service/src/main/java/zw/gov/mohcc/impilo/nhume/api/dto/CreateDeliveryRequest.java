package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Payload accepted by POST /api/v1/nhume/deliveries.
 *
 * <p>Most fields are optional so the same payload type supports lightweight
 * citizen-app requests through to fully-specified facility/programme bulk
 * uploads.
 */
public record CreateDeliveryRequest(
        @JsonProperty("delivery_type") String deliveryType,
        @JsonProperty("priority") String priority,
        @NotBlank @JsonProperty("request_source") String requestSource,
        @JsonProperty("requesting_actor_id") String requestingActorId,
        @JsonProperty("requesting_actor_type") String requestingActorType,
        @JsonProperty("requesting_org_id") String requestingOrgId,
        @JsonProperty("requesting_facility_id") String requestingFacilityId,
        @JsonProperty("origin") LocationDto origin,
        @JsonProperty("destination") LocationDto destination,
        @JsonProperty("recipient") RecipientDto recipient,
        @JsonProperty("items") List<DeliveryItemDto> items,
        @JsonProperty("packages") List<DeliveryPackageDto> packages,
        @JsonProperty("clinical_context_ref") String clinicalContextRef,
        @JsonProperty("programme_context_ref") String programmeContextRef,
        @JsonProperty("telehealth_session_ref") String telehealthSessionRef,
        @JsonProperty("marketplace_order_ref") String marketplaceOrderRef,
        @JsonProperty("payment_path") String paymentPath,
        @JsonProperty("payment_reference") String paymentReference,
        @JsonProperty("declared_value_cents") Long declaredValueCents,
        @JsonProperty("currency") String currency,
        @JsonProperty("consent_required") Boolean consentRequired,
        @JsonProperty("identity_verification") String identityVerification,
        @JsonProperty("cold_chain_required") Boolean coldChainRequired,
        @JsonProperty("controlled_item") Boolean controlledItem,
        @JsonProperty("fragile") Boolean fragile,
        @JsonProperty("hazardous") Boolean hazardous,
        @JsonProperty("biohazard") Boolean biohazard,
        @JsonProperty("specimen") Boolean specimen,
        @JsonProperty("chain_of_custody_required") Boolean chainOfCustodyRequired,
        @JsonProperty("return_required") Boolean returnRequired,
        @JsonProperty("required_by") OffsetDateTime requiredBy,
        @JsonProperty("pickup_window_start") OffsetDateTime pickupWindowStart,
        @JsonProperty("pickup_window_end") OffsetDateTime pickupWindowEnd,
        @JsonProperty("delivery_window_start") OffsetDateTime deliveryWindowStart,
        @JsonProperty("delivery_window_end") OffsetDateTime deliveryWindowEnd,
        @JsonProperty("allowed_modes") List<String> allowedModes,
        @JsonProperty("policy_id") String policyId,
        @JsonProperty("sla_id") String slaId,
        @JsonProperty("notes") String notes,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("submit_immediately") Boolean submitImmediately,
        // OF-B18 §12.4 — per-order-class handover contract (both optional; NULL keeps
        // the legacy ungraded behaviour so existing callers are unaffected).
        @JsonProperty("required_pod_grade") String requiredPodGrade,
        @JsonProperty("named_recipient_required") Boolean namedRecipientRequired
) {

    public record LocationDto(
            @JsonProperty("kind") String kind,
            @JsonProperty("ref") String ref,
            @JsonProperty("label") String label,
            @JsonProperty("address") String address,
            @JsonProperty("lat") Double lat,
            @JsonProperty("lng") Double lng
    ) {}

    public record RecipientDto(
            @JsonProperty("kind") String kind,
            @JsonProperty("ref") String ref,
            @JsonProperty("name") String name,
            @JsonProperty("phone") String phone,
            @JsonProperty("email") String email,
            @JsonProperty("language") String language
    ) {}

    public record DeliveryItemDto(
            @JsonProperty("item_kind") String itemKind,
            @JsonProperty("item_ref") String itemRef,
            @JsonProperty("description") String description,
            @JsonProperty("quantity") java.math.BigDecimal quantity,
            @JsonProperty("unit_of_measure") String unitOfMeasure,
            @JsonProperty("package_type") String packageType,
            @JsonProperty("cold_chain") Boolean coldChain,
            @JsonProperty("controlled") Boolean controlled
    ) {}

    public record DeliveryPackageDto(
            @JsonProperty("package_code") String packageCode,
            @JsonProperty("package_type") String packageType,
            @JsonProperty("seal_id") String sealId,
            @JsonProperty("cold_chain") Boolean coldChain,
            @JsonProperty("fragile") Boolean fragile
    ) {}
}
