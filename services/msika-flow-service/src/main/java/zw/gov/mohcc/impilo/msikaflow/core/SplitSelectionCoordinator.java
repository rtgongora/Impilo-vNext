package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.SelectionEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferLineRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.MarketplaceRequestRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.SelectionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * OF-B14 — split fulfilment: the patient selects a COMBINATION of offers that
 * together cover the request (§8.6.4, journey #45).
 *
 * <p><b>Composition, not a bespoke split machine:</b> each member runs the
 * existing §8.9 twelve-step {@link CommitmentService#commit} SERIALLY with its
 * own derived idempotency key and its own transaction — per-member compensation
 * isolation is therefore structural: a failing member compensates only its own
 * holds/claim/payment. Members already committed <b>STAND</b> (§8.6.4 — no
 * invented all-or-nothing rollback across vendors); the failed members' lines
 * return to the honest {@code OFFERS_AVAILABLE} posture and are re-offerable.</p>
 *
 * <p>The request rolls to {@code COMMITTED} only when the union of committed
 * coverage spans every published line (enforced inside
 * {@code CommitmentService.completeCommitment}); otherwise the overall outcome
 * here is the honest {@code PARTIAL_COMMIT} with coded per-member results.</p>
 *
 * <p><b>Not transactional as a whole — deliberately:</b> wrapping the loop in
 * one transaction would silently recreate cross-vendor rollback.</p>
 */
@Service
public class SplitSelectionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SplitSelectionCoordinator.class);

    /** Separator between the caller's Idempotency-Key and the member offer id. */
    static final String MEMBER_KEY_SEPARATOR = "#";
    private static final int MAX_BASE_KEY_LENGTH = 100; // column is 128; 26-char offer id + separator must fit

    // Combination refusal codes (validated BEFORE any member commits)
    public static final String CODE_SPLIT_REQUIRES_MULTIPLE_OFFERS = "SPLIT_REQUIRES_MULTIPLE_OFFERS";
    public static final String CODE_DUPLICATE_MEMBER = "SPLIT_DUPLICATE_MEMBER";
    public static final String CODE_MEMBER_NOT_FOUND = "SPLIT_MEMBER_NOT_FOUND";
    public static final String CODE_MEMBER_NOT_ACTIVE = "SPLIT_MEMBER_NOT_ACTIVE";
    public static final String CODE_OVERLAPPING_LINE_COVERAGE = "OVERLAPPING_LINE_COVERAGE";

    // Overall outcomes
    public static final String OUTCOME_COMMITTED = "COMMITTED";
    public static final String OUTCOME_PARTIAL_COMMIT = "PARTIAL_COMMIT";
    public static final String OUTCOME_AWAITING_PAYMENT = "AWAITING_PAYMENT";
    public static final String OUTCOME_FAILED = "FAILED";

    /** Per-member coded result — {@code selectionId} null when refused pre-commit. */
    public record MemberResult(String offerId, String selectionId, String status,
                               String outcomeCode, boolean committed, boolean replayed) {}

    /**
     * Honest combined-coverage summary: {@code complete} is line-ref coverage;
     * {@code underSuppliedLineRefs} lists covered lines whose combined offered
     * quantity is below the requested quantity (partial-quantity honesty).
     */
    public record Coverage(boolean complete, List<String> coveredLineRefs,
                           List<String> uncoveredLineRefs, List<String> underSuppliedLineRefs) {}

    public record SplitResult(String splitGroupId, String outcome, Coverage coverage,
                              List<MemberResult> members) {}

    private final MarketplaceRequestRepository requestRepository;
    private final FulfillmentOfferRepository offerRepository;
    private final FulfillmentOfferLineRepository offerLineRepository;
    private final SelectionRepository selectionRepository;
    private final EventOutboxRepository outboxRepository;
    private final CommitmentService commitmentService;
    private final MarketplaceRequestService marketplaceRequestService;
    private final ObjectMapper objectMapper;

    public SplitSelectionCoordinator(MarketplaceRequestRepository requestRepository,
                                     FulfillmentOfferRepository offerRepository,
                                     FulfillmentOfferLineRepository offerLineRepository,
                                     SelectionRepository selectionRepository,
                                     EventOutboxRepository outboxRepository,
                                     CommitmentService commitmentService,
                                     MarketplaceRequestService marketplaceRequestService,
                                     ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.offerRepository = offerRepository;
        this.offerLineRepository = offerLineRepository;
        this.selectionRepository = selectionRepository;
        this.outboxRepository = outboxRepository;
        this.commitmentService = commitmentService;
        this.marketplaceRequestService = marketplaceRequestService;
        this.objectMapper = objectMapper;
    }

    public SplitResult selectSplit(String requestId, List<String> offerIds, UUID tenantId,
                                   String actorId, String idempotencyKey, HttpServletRequest inbound) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for split selection (RC-1)");
        }
        if (idempotencyKey.length() > MAX_BASE_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key too long for split selection (max " + MAX_BASE_KEY_LENGTH + " chars)");
        }
        if (offerIds == null || offerIds.size() < 2) {
            throw new IllegalArgumentException(CODE_SPLIT_REQUIRES_MULTIPLE_OFFERS
                    + ": a split selection needs at least two member offers — use /select for one");
        }
        Set<String> distinct = new LinkedHashSet<>(offerIds);
        if (distinct.size() != offerIds.size()) {
            throw new IllegalArgumentException(CODE_DUPLICATE_MEMBER
                    + ": the same offer appears more than once in the combination");
        }

        MarketplaceRequestEntity request = requestRepository.findByRequestIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Marketplace request not found: " + requestId));
        // RC-1 replay: when EVERY member key already has a selection this call is
        // an idempotent replay — it must return the first result even after the
        // request rolled to COMMITTED, so the status gate applies to fresh
        // combinations only. (A retry of failed members needs a NEW key —
        // replaying a FAILED member returns its recorded failure, per RC-1.)
        boolean allMembersReplay = distinct.stream().allMatch(offerId ->
                selectionRepository.findFirstByTenantIdAndIdempotencyKey(
                        tenantId, memberKey(idempotencyKey, offerId)).isPresent());
        if (!allMembersReplay
                && request.getStatus() != MarketplaceRequestStatus.OFFERS_AVAILABLE
                && request.getStatus() != MarketplaceRequestStatus.SELECTION_PENDING) {
            throw new IllegalStateException("REQUEST_NOT_SELECTABLE: request status " + request.getStatus());
        }

        // ── Combination validation — BEFORE any member commits ──
        Map<String, Set<String>> memberCoverage = new LinkedHashMap<>();
        Map<String, Integer> offeredQuantities = new HashMap<>();
        for (String offerId : distinct) {
            FulfillmentOfferEntity offer = offerRepository.findByOfferIdAndTenantId(offerId, tenantId)
                    .filter(o -> requestId.equals(o.getRequestId()))
                    .orElseThrow(() -> new IllegalArgumentException(CODE_MEMBER_NOT_FOUND
                            + ": offer " + offerId + " not found on request " + requestId));
            boolean memberReplay = selectionRepository.findFirstByTenantIdAndIdempotencyKey(
                    tenantId, memberKey(idempotencyKey, offerId)).isPresent();
            if (offer.getStatus() != OfferStatus.ACTIVE && !memberReplay) {
                throw new IllegalStateException(CODE_MEMBER_NOT_ACTIVE
                        + ": offer " + offerId + " is " + offer.getStatus() + " — combination refused");
            }
            Set<String> refs = new LinkedHashSet<>();
            for (FulfillmentOfferLineEntity line : offerLineRepository.findByOfferId(offerId)) {
                if (line.getRequestLineRef() != null) {
                    refs.add(line.getRequestLineRef());
                    offeredQuantities.merge(line.getRequestLineRef(), line.getQuantity(), Integer::sum);
                }
            }
            memberCoverage.put(offerId, refs);
        }
        assertNoOverlap(memberCoverage);
        Coverage coverage = combinedCoverage(request, memberCoverage, offeredQuantities);

        // ── Split group id — stable across replays via the first member's stored id ──
        String splitGroupId = distinct.stream()
                .map(offerId -> selectionRepository.findFirstByTenantIdAndIdempotencyKey(
                        tenantId, memberKey(idempotencyKey, offerId)))
                .filter(Optional::isPresent)
                .map(s -> s.get().getSplitGroupId())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseGet(UlidGenerator::generate);

        // ── Serial member commits — per-member transactions, per-member compensation ──
        List<MemberResult> members = new ArrayList<>();
        for (String offerId : distinct) {
            try {
                CommitmentService.CommitResult result = commitmentService.commit(
                        requestId, offerId, tenantId, actorId,
                        memberKey(idempotencyKey, offerId), splitGroupId, inbound);
                members.add(new MemberResult(offerId, result.selection().getSelectionId(),
                        result.selection().getStatus().name(), result.outcomeCode(),
                        result.committed(), result.replayed()));
            } catch (RuntimeException e) {
                // A refused member (state conflict, precondition) is a coded
                // per-member outcome — siblings continue; committed members STAND.
                log.warn("Split member {} refused on request {}: {}", offerId, requestId, e.getMessage());
                members.add(new MemberResult(offerId, null, "REFUSED",
                        refusalCode(e), false, false));
            }
        }

        String outcome = deriveOutcome(members);
        publishSplitOutcomeEvent(request, splitGroupId, outcome, coverage, members);
        log.info("Split selection {} on request {}: outcome={} members={} complete={}",
                splitGroupId, requestId, outcome, members.size(), coverage.complete());
        return new SplitResult(splitGroupId, outcome, coverage, members);
    }

    static String memberKey(String idempotencyKey, String offerId) {
        return idempotencyKey + MEMBER_KEY_SEPARATOR + offerId;
    }

    /** §8.6.4 / RC-1 per-line: no two members may cover the same request line. */
    private void assertNoOverlap(Map<String, Set<String>> memberCoverage) {
        Map<String, String> seen = new HashMap<>(); // lineRef -> offerId
        for (Map.Entry<String, Set<String>> member : memberCoverage.entrySet()) {
            for (String ref : member.getValue()) {
                String previous = seen.putIfAbsent(ref, member.getKey());
                if (previous != null) {
                    throw new IllegalArgumentException(CODE_OVERLAPPING_LINE_COVERAGE
                            + ": line " + ref + " is covered by both offer " + previous
                            + " and offer " + member.getKey() + " — a line has exactly one fulfiller");
                }
            }
        }
    }

    /** Honest combined completeness — surfaced, never assumed (§8.6.4 splitting warnings ride the BFF/UI). */
    private Coverage combinedCoverage(MarketplaceRequestEntity request,
                                      Map<String, Set<String>> memberCoverage,
                                      Map<String, Integer> offeredQuantities) {
        Map<String, Integer> requested = marketplaceRequestService.requestLineQuantities(request);
        Set<String> covered = new LinkedHashSet<>();
        memberCoverage.values().forEach(covered::addAll);
        List<String> coveredRefs = new ArrayList<>();
        List<String> uncoveredRefs = new ArrayList<>();
        List<String> underSupplied = new ArrayList<>();
        for (Map.Entry<String, Integer> line : requested.entrySet()) {
            if (covered.contains(line.getKey())) {
                coveredRefs.add(line.getKey());
                Integer offered = offeredQuantities.get(line.getKey());
                if (offered == null || offered < line.getValue()) {
                    underSupplied.add(line.getKey());
                }
            } else {
                uncoveredRefs.add(line.getKey());
            }
        }
        boolean complete = !requested.isEmpty() && uncoveredRefs.isEmpty();
        return new Coverage(complete, coveredRefs, uncoveredRefs, underSupplied);
    }

    private String deriveOutcome(List<MemberResult> members) {
        long committed = members.stream().filter(MemberResult::committed).count();
        long awaiting = members.stream()
                .filter(m -> CommitmentService.CODE_AWAITING_PAYMENT.equals(m.outcomeCode()))
                .count();
        long failedOrRefused = members.size() - committed - awaiting;
        if (committed == members.size()) {
            return OUTCOME_COMMITTED;
        }
        if (committed == 0 && awaiting == 0) {
            return OUTCOME_FAILED;
        }
        if (failedOrRefused == 0) {
            return OUTCOME_AWAITING_PAYMENT;
        }
        return OUTCOME_PARTIAL_COMMIT;
    }

    private String refusalCode(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "MEMBER_REFUSED";
        }
        int colon = message.indexOf(':');
        String head = colon > 0 ? message.substring(0, colon) : message;
        return head.matches("[A-Z0-9_]+") ? head : "MEMBER_REFUSED";
    }

    /** PII-free split outcome event — ids, refs, coded outcomes only. */
    private void publishSplitOutcomeEvent(MarketplaceRequestEntity request, String splitGroupId,
                                          String outcome, Coverage coverage, List<MemberResult> members) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", request.getRequestId());
            payload.put("tenantId", request.getTenantId().toString());
            payload.put("splitGroupId", splitGroupId);
            payload.put("outcome", outcome);
            payload.put("combinedCoverageComplete", coverage.complete());
            payload.put("uncoveredLineRefs", coverage.uncoveredLineRefs());
            payload.put("underSuppliedLineRefs", coverage.underSuppliedLineRefs());
            List<Map<String, Object>> memberPayloads = new ArrayList<>();
            for (MemberResult member : members) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("offerId", member.offerId());
                m.put("selectionId", member.selectionId());
                m.put("status", member.status());
                m.put("outcomeCode", member.outcomeCode());
                memberPayloads.add(m);
            }
            payload.put("members", memberPayloads);
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("MarketplaceRequest");
            outbox.setAggregateId(request.getRequestId());
            outbox.setEventType("SPLIT_SELECTION_OUTCOME");
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(request.getTenantId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write SPLIT_SELECTION_OUTCOME event for request {}: {}",
                    request.getRequestId(), e.getMessage());
        }
    }
}
