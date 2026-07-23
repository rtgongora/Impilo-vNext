package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaflow.domain.FundingMode;
import zw.gov.mohcc.impilo.msikaflow.integration.CostaClient;
import zw.gov.mohcc.impilo.msikaflow.integration.CoverageClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OF-B9 (+ OF-B8 PA posture) — the per-offer financial block (§8.7/§10.4).
 *
 * <p>Composes the money-stack ladder for ONE offer: COSTA constructs the
 * standard charge for the coded lines → Ruvimbo's LIVE
 * {@code cv_liability_estimates} engine decomposes it into payer/patient
 * shares per line → the LIVE 14-status {@code cv_authorisations} machine
 * supplies the PA posture where a benefit requires authorisation. msika-flow
 * itself decides NOTHING financial (§10.1) — it orchestrates and projects.</p>
 *
 * <p>Honesty rules, all binding:</p>
 * <ul>
 *   <li>§10.5 — every figure is an ESTIMATE ({@code estimateNeverFinal:true}
 *       is carried on every block; no block ever claims finality).</li>
 *   <li>§10.4 degradation — engines unreachable → status {@code UNVERIFIED}
 *       (display truth); commitment step 7 fails CLOSED for payer-covered
 *       flows and proceeds only for the explicit self-pay election.</li>
 *   <li>Benefit-code note — until the OF-B8 payer-formulary layer
 *       ({@code cv_formulary}) lands, the offer line's coded {@code itemCode}
 *       is passed as the benefit code; plans that don't define that code
 *       price honestly as non-covered (full charge = patient share), never
 *       silently as covered.</li>
 * </ul>
 */
@Service
public class OfferFinancialsService {

    private static final Logger log = LoggerFactory.getLogger(OfferFinancialsService.class);

    // Block statuses (display truth + step-7 gate inputs)
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_SELF_PAY = "SELF_PAY";
    public static final String STATUS_NOT_COVERED = "NOT_COVERED";
    public static final String STATUS_UNVERIFIED = "UNVERIFIED";

    // PA postures (projection over the live cv_authorisations statuses, §9.5)
    public static final String PA_NOT_REQUIRED = "NOT_REQUIRED";
    public static final String PA_APPROVED = "APPROVED";
    public static final String PA_PARTIALLY_APPROVED = "PARTIALLY_APPROVED";
    public static final String PA_PENDING = "PENDING";
    public static final String PA_NOT_APPROVED = "NOT_APPROVED";
    public static final String PA_UNVERIFIED = "UNVERIFIED";

    /** The per-offer financial block attached to offer views and the step-7 snapshot. */
    public record FinancialBlock(
            String status,
            FundingMode fundingMode,
            String currency,
            BigDecimal grossCharge,
            BigDecimal coveredAmount,
            BigDecimal patientLiability,
            UUID calculationId,
            List<UUID> estimateIds,
            String memberCpid,
            boolean paRequired,
            String paStatus,
            UUID paReference,
            String detail,
            OffsetDateTime computedAt
    ) {
        /** True only when step 7 may pass for a payer-covered flow. */
        public boolean payerVerified() {
            return STATUS_VERIFIED.equals(status);
        }

        /** True when the PA posture (if any) permits commitment (§8.7.6). */
        public boolean paSatisfied() {
            return !paRequired || PA_APPROVED.equals(paStatus) || PA_PARTIALLY_APPROVED.equals(paStatus);
        }

        /** The due-now shortfall this block implies (never null for SELF_PAY/VERIFIED). */
        public BigDecimal shortfall() {
            return patientLiability;
        }
    }

    private final CostaClient costaClient;
    private final CoverageClient coverageClient;
    private final ObjectMapper objectMapper;

    public OfferFinancialsService(CostaClient costaClient, CoverageClient coverageClient,
                                  ObjectMapper objectMapper) {
        this.costaClient = costaClient;
        this.coverageClient = coverageClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Compute the financial block for one offer (comparison listing AND the
     * commitment step-7 re-verification both call this — same code path, no
     * listing/checkout divergence).
     */
    public FinancialBlock compute(MarketplaceRequestEntity request, FulfillmentOfferEntity offer,
                                  List<FulfillmentOfferLineEntity> lines, HttpServletRequest inbound) {
        OffsetDateTime now = OffsetDateTime.now();
        FundingMode mode = effectiveFundingMode(request);

        if (mode == FundingMode.SELF_PAY) {
            // Explicit out-of-pocket election: full offer price is the due-now shortfall.
            return new FinancialBlock(STATUS_SELF_PAY, mode, offer.getCurrency(),
                    offer.getPriceTotal(), BigDecimal.ZERO, offer.getPriceTotal(),
                    null, List.of(), null, false, PA_NOT_REQUIRED, null,
                    "self-pay elected — coverage engines not consulted", now);
        }

        UUID coverageId = request.getCoverageId();
        if (coverageId == null) {
            // Payer-covered claimed but no coverage reference — fail-closed ambiguity.
            return unverified(mode, offer, "PAYER_COVERED without a coverageId — cannot verify", now);
        }

        // ── COSTA supplies the charge (§10.4) ────────────────────────────
        List<CostaClient.ChargeLine> chargeLines = lines.stream()
                .map(l -> new CostaClient.ChargeLine(l.getItemCode(), l.getQuantity()))
                .toList();
        Optional<CostaClient.CostaCharge> charge = costaClient.chargeForLines(chargeLines, inbound);
        if (charge.isEmpty()) {
            return unverified(mode, offer, "COSTA charge engine unreachable", now);
        }

        // ── Ruvimbo decomposes per line (LIVE cv_liability_estimates) ────
        BigDecimal covered = BigDecimal.ZERO;
        BigDecimal patient = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        List<UUID> estimateIds = new ArrayList<>();
        List<String> paBenefitCodes = new ArrayList<>();
        Map<String, BigDecimal> paAmounts = new LinkedHashMap<>();
        String currency = null;
        String memberCpid = null;
        for (FulfillmentOfferLineEntity line : lines) {
            BigDecimal lineCharge = charge.get().lineCharges().get(line.getItemCode());
            if (lineCharge == null) {
                // COSTA priced the batch but not this code — an unpriceable line
                // makes the whole offer unverifiable, never partially trusted.
                return unverified(mode, offer,
                        "COSTA returned no charge for line " + line.getItemCode(), now);
            }
            CoverageClient.EstimateResult result =
                    coverageClient.estimateLiability(coverageId, line.getItemCode(), lineCharge, inbound);
            if (result.outcome() == CoverageClient.Outcome.UNREACHABLE) {
                return unverified(mode, offer, "Ruvimbo liability engine unreachable", now);
            }
            if (result.outcome() == CoverageClient.Outcome.REFUSED) {
                return new FinancialBlock(STATUS_NOT_COVERED, mode, offer.getCurrency(),
                        null, null, null, null, List.of(estimateIds.toArray(UUID[]::new)), memberCpid,
                        false, PA_NOT_REQUIRED, null,
                        "coverage " + coverageId + " not resolvable on Ruvimbo", now);
            }
            CoverageClient.LiabilityEstimate estimate = result.estimate();
            gross = gross.add(nz(estimate.standardCharge()));
            covered = covered.add(nz(estimate.payerEstimate()));
            patient = patient.add(nz(estimate.patientResponsibility()));
            estimateIds.add(estimate.estimateId());
            if (currency == null) {
                currency = estimate.currency();
            }
            if (memberCpid == null) {
                memberCpid = estimate.memberCpid();
            }
            if (estimate.requiresAuthorisation()) {
                paBenefitCodes.add(estimate.benefitCode());
                paAmounts.put(estimate.benefitCode(), nz(estimate.payerEstimate()));
            }
        }

        // ── PA posture (OF-B8 — wire the LIVE machine, never a new one) ──
        boolean paRequired = !paBenefitCodes.isEmpty();
        String paStatus = PA_NOT_REQUIRED;
        UUID paReference = null;
        if (paRequired) {
            PaPosture posture = resolvePaPosture(coverageId, memberCpid, paBenefitCodes, paAmounts, inbound);
            paStatus = posture.status();
            paReference = posture.reference();
        }

        return new FinancialBlock(STATUS_VERIFIED, mode,
                currency != null ? currency : offer.getCurrency(),
                gross, covered, patient,
                estimateIds.isEmpty() ? null : estimateIds.get(0),
                List.copyOf(estimateIds), memberCpid,
                paRequired, paStatus, paReference, null, now);
    }

    private record PaPosture(String status, UUID reference) {}

    /**
     * §8.7.5 — determine the PA posture for the benefit codes that require it:
     * an existing APPROVED/PARTIALLY_APPROVED authorisation covering ALL of
     * them satisfies; a live pending one is PENDING; none at all triggers the
     * minimum-necessary auto-submit (steps 1–3) and is PENDING; a denied-only
     * posture is NOT_APPROVED; an unreachable machine is UNVERIFIED (fail
     * closed at step 7 — an unreachable payer never self-approves).
     */
    private PaPosture resolvePaPosture(UUID coverageId, String memberCpid,
                                       List<String> benefitCodes, Map<String, BigDecimal> amounts,
                                       HttpServletRequest inbound) {
        if (memberCpid == null) {
            return new PaPosture(PA_UNVERIFIED, null);
        }
        Optional<List<CoverageClient.Authorisation>> lookup =
                coverageClient.findAuthorisations(memberCpid, inbound);
        if (lookup.isEmpty()) {
            return new PaPosture(PA_UNVERIFIED, null);
        }
        CoverageClient.Authorisation pending = null;
        CoverageClient.Authorisation denied = null;
        for (CoverageClient.Authorisation auth : lookup.get()) {
            if (!auth.benefitCodes().containsAll(benefitCodes)) {
                continue;
            }
            String status = auth.status() != null ? auth.status() : "";
            switch (status) {
                case "APPROVED" -> {
                    return new PaPosture(PA_APPROVED, auth.id());
                }
                case "PARTIALLY_APPROVED" -> {
                    return new PaPosture(PA_PARTIALLY_APPROVED, auth.id());
                }
                case "SUBMITTED", "ACKNOWLEDGED", "IN_REVIEW", "INFORMATION_REQUESTED", "APPEALED" ->
                        pending = auth;
                case "DENIED", "EXPIRED" -> denied = auth;
                default -> { /* DRAFT/WITHDRAWN/CANCELLED/USED/OVERTURNED: not a live posture for this flow */ }
            }
        }
        if (pending != null) {
            return new PaPosture(PA_PENDING, pending.id());
        }
        if (denied != null) {
            return new PaPosture(PA_NOT_APPROVED, denied.id());
        }
        // No authorisation exists — initiate the nine-step flow (steps 1–3).
        Optional<UUID> submitted = coverageClient.submitAuthorisation(coverageId, benefitCodes, amounts, inbound);
        return submitted
                .map(id -> new PaPosture(PA_PENDING, id))
                .orElseGet(() -> {
                    log.warn("PA auto-submit failed for coverage {} — posture UNVERIFIED", coverageId);
                    return new PaPosture(PA_UNVERIFIED, null);
                });
    }

    private FinancialBlock unverified(FundingMode mode, FulfillmentOfferEntity offer,
                                      String detail, OffsetDateTime now) {
        return new FinancialBlock(STATUS_UNVERIFIED, mode, offer.getCurrency(),
                null, null, null, null, List.of(), null, false, PA_UNVERIFIED, null, detail, now);
    }

    private FundingMode effectiveFundingMode(MarketplaceRequestEntity request) {
        // Fail-closed default: no explicit payer election means self-pay display
        // truth — a coverage promise is never fabricated from silence.
        return request.getFundingMode() != null ? request.getFundingMode() : FundingMode.SELF_PAY;
    }

    /** Serialises the block for offer views, the step-7 snapshot and events. */
    public String toJson(FinancialBlock block) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", block.status());
        node.put("fundingMode", block.fundingMode().name());
        node.put("currency", block.currency());
        putDecimal(node, "grossCharge", block.grossCharge());
        putDecimal(node, "coveredAmount", block.coveredAmount());
        putDecimal(node, "patientLiability", block.patientLiability());
        if (block.calculationId() != null) {
            node.put("calculationId", block.calculationId().toString());
        }
        ArrayNode ids = node.putArray("estimateIds");
        block.estimateIds().forEach(id -> ids.add(id.toString()));
        node.put("paRequired", block.paRequired());
        node.put("paStatus", block.paStatus());
        if (block.paReference() != null) {
            node.put("paReference", block.paReference().toString());
        }
        // §10.5 binding label — carried on EVERY block, unconditionally.
        node.put("estimateNeverFinal", true);
        if (block.detail() != null) {
            node.put("detail", block.detail());
        }
        node.put("computedAt", block.computedAt().toString());
        return node.toString();
    }

    private static void putDecimal(ObjectNode node, String field, BigDecimal value) {
        if (value != null) {
            node.put(field, value.toPlainString());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
