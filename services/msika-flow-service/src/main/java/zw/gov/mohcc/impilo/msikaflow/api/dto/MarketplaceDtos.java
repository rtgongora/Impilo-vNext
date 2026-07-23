package zw.gov.mohcc.impilo.msikaflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import zw.gov.mohcc.impilo.msikaflow.domain.FundingMode;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceProfile;
import zw.gov.mohcc.impilo.msikaflow.domain.PublicationMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OF-B4/OF-B6 request/response shapes for {@code /v1/marketplace-requests}.
 *
 * <p>Request lines are CODED by contract (§8.3 allow-list): item codes,
 * quantities and constraint flags only — the API never accepts or returns
 * patient identity, diagnosis, prescriber identity or addresses.</p>
 */
public final class MarketplaceDtos {

    private MarketplaceDtos() {}

    public record RequestLineDto(
            String lineRef,
            @NotBlank String itemCode,
            String codingSystem,
            @Positive int quantity,
            String unit,
            boolean controlled,
            boolean coldChain,
            Boolean substitutionAllowed,
            /* OF-B13 — coded imaging modality / procedure class for DIAGNOSTICS lines (§11.8 capability flag) */
            String modality
    ) {}

    public record CreateRequestDto(
            @NotBlank String orosOrderId,
            @NotBlank String orosOrderVersionId,
            @NotNull MarketplaceProfile profile,
            @NotNull PublicationMode publicationMode,
            String coarseZone,
            String urgency,
            @NotEmpty List<RequestLineDto> lines,
            List<String> invitedVendorIds,
            String prescriptionToken,
            /* OF-B8/OF-B9 §10.3 — funding election; null => SELF_PAY (fail-closed) */
            FundingMode fundingMode,
            /* Ruvimbo member-coverage ref; required when fundingMode=PAYER_COVERED */
            UUID coverageId,
            /* OF-B13 — DIAGNOSTICS only: phlebotomy home-collection requirement */
            Boolean homeCollectionRequested
    ) {}

    public record RequestView(
            String requestId,
            String orosOrderId,
            String orosOrderVersionId,
            String profile,
            String publicationMode,
            String status,
            boolean controlled,
            String coarseZone,
            String urgency,
            OffsetDateTime offerWindowEndsAt,
            OffsetDateTime selectionWindowEndsAt,
            JsonNode publishedLines
    ) {}

    public record PublishResponse(
            RequestView request,
            int invitationCount,
            java.util.Map<String, String> refusals
    ) {}

    public record OfferLineDto(
            @NotBlank String requestLineRef,
            @NotBlank String itemCode,
            @Positive int quantity,
            BigDecimal unitPrice,
            boolean substitutionProposed
    ) {}

    public record SubmitOfferDto(
            @NotBlank String vendorId,
            @NotNull BigDecimal priceTotal,
            String currency,
            Integer fulfillmentWindowHours,
            Long ttlMinutes,
            @NotEmpty List<OfferLineDto> lines,
            /* OF-B13 — DIAGNOSTICS only: home specimen collection with a mandatory window */
            Boolean homeCollection,
            OffsetDateTime collectionWindowStart,
            OffsetDateTime collectionWindowEnd
    ) {}

    public record OfferLineView(
            String offerLineId,
            String requestLineRef,
            String itemCode,
            int quantity,
            BigDecimal unitPrice,
            String stockGrade,
            boolean substitutionProposed
    ) {}

    public record OfferView(
            String offerId,
            String requestId,
            String vendorId,
            String status,
            String statusReason,
            BigDecimal priceTotal,
            String currency,
            Integer fulfillmentWindowHours,
            OffsetDateTime ttlExpiresAt,
            List<OfferLineView> lines,
            List<String> rankedBecause,
            /* OF-B9 §10.4 per-offer financial block (estimateNeverFinal:true inside);
               null on non-comparison views where financials were not computed */
            JsonNode financials,
            /* OF-B13 — home specimen collection declaration + window */
            boolean homeCollection,
            OffsetDateTime collectionWindowStart,
            OffsetDateTime collectionWindowEnd,
            /* OF-B14 §8.6.1 — honest partial posture: request lines this offer
               misses (absent or under-quantity); null on non-comparison views */
            List<String> uncoveredLineRefs
    ) {}

    public record SelectDto(@NotBlank String offerId) {}

    /* OF-B14 — patient-chosen offer combination (§8.6.4, journey #45). */
    public record SelectSplitDto(@NotEmpty List<String> offerIds) {}

    public record SplitMemberView(
            String offerId,
            String selectionId,
            String status,
            String outcomeCode,
            boolean committed,
            boolean replayed
    ) {}

    public record SplitCoverageView(
            boolean complete,
            List<String> coveredLineRefs,
            List<String> uncoveredLineRefs,
            List<String> underSuppliedLineRefs
    ) {}

    public record SplitSelectionView(
            String splitGroupId,
            String requestId,
            /* COMMITTED | PARTIAL_COMMIT | AWAITING_PAYMENT | FAILED — honest rollup */
            String outcome,
            SplitCoverageView coverage,
            List<SplitMemberView> members
    ) {}

    public record SelectionView(
            String selectionId,
            String requestId,
            String offerId,
            String status,
            String outcomeCode,
            boolean committed,
            boolean replayed,
            String prescriptionClaimId,
            OffsetDateTime committedAt,
            JsonNode stepLog,
            /* OF-B9/OF-B10 — step-7 financial snapshot + payment posture */
            JsonNode financials,
            String paymentIntentId,
            String paymentStatus,
            BigDecimal shortfallAmount,
            String shortfallCurrency,
            String paStatus,
            String paReference,
            /* OF-B14 — split membership (null for single-offer selections) */
            String splitGroupId,
            /* OF-B13 — OROS spine seam + fulfilment expectation */
            String orosOrderId,
            String collectionMode,
            OffsetDateTime collectionWindowStart,
            OffsetDateTime collectionWindowEnd
    ) {}

    public record WithdrawDto(String reason) {}

    public record CancelDto(String reason) {}
}
