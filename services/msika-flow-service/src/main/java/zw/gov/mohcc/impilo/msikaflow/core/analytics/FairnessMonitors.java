package zw.gov.mohcc.impilo.msikaflow.core.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingType;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.SelectionStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.RankingSnapshotEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.SelectionEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.MarketplaceRequestRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.RankingSnapshotRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.SelectionRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * OF-B29 §21 — marketplace-fairness monitors, the operational-duty half of
 * "fairness monitoring as a named duty" (§21.5): concentration watch,
 * ranking-transparency audit, no-offer equity review. Deterministic
 * rule + threshold checks persisted as accountable findings; collusion
 * screening (bid clustering) and statistical baselines are documented future
 * lanes.
 */
@Service
public class FairnessMonitors {

    private static final Logger log = LoggerFactory.getLogger(FairnessMonitors.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Post-hoc audit candidate set — the comparison set as it stood, approximated (documented). */
    private static final Set<OfferStatus> AUDIT_CANDIDATE_STATUSES =
            EnumSet.of(OfferStatus.ACTIVE, OfferStatus.SELECTED, OfferStatus.REVALIDATING,
                    OfferStatus.COMMITTED, OfferStatus.NOT_SELECTED, OfferStatus.EXPIRED,
                    OfferStatus.FAILED_REVALIDATION);

    /** Requests counted as "published to the market" for equity rates. */
    private static final Set<MarketplaceRequestStatus> PUBLISHED_STATUSES =
            EnumSet.of(MarketplaceRequestStatus.PUBLISHED, MarketplaceRequestStatus.COLLECTING_OFFERS,
                    MarketplaceRequestStatus.OFFERS_AVAILABLE, MarketplaceRequestStatus.SELECTION_PENDING,
                    MarketplaceRequestStatus.COMMITTED, MarketplaceRequestStatus.FAILED_NO_OFFERS,
                    MarketplaceRequestStatus.EXPIRED);

    private final SelectionRepository selectionRepository;
    private final MarketplaceRequestRepository requestRepository;
    private final FulfillmentOfferRepository offerRepository;
    private final RankingSnapshotRepository snapshotRepository;
    private final RankedBecauseRecomputer recomputer;
    private final AnomalyFindingService findingService;
    private final ObjectMapper objectMapper;

    private final long concentrationWindowHours;
    private final BigDecimal concentrationThreshold;
    private final int concentrationMinVolume;
    private final long rankingAuditLookbackHours;
    private final BigDecimal noOfferRateThreshold;
    private final int noOfferMinRequests;
    private final long noOfferWindowHours;

    public FairnessMonitors(SelectionRepository selectionRepository,
                            MarketplaceRequestRepository requestRepository,
                            FulfillmentOfferRepository offerRepository,
                            RankingSnapshotRepository snapshotRepository,
                            RankedBecauseRecomputer recomputer,
                            AnomalyFindingService findingService,
                            ObjectMapper objectMapper,
                            @Value("${msika-flow.analytics.concentration-window-hours:168}") long concentrationWindowHours,
                            @Value("${msika-flow.analytics.concentration-threshold:0.50}") BigDecimal concentrationThreshold,
                            @Value("${msika-flow.analytics.concentration-min-volume:10}") int concentrationMinVolume,
                            @Value("${msika-flow.analytics.ranking-audit-lookback-hours:72}") long rankingAuditLookbackHours,
                            @Value("${msika-flow.analytics.no-offer-rate-threshold:0.50}") BigDecimal noOfferRateThreshold,
                            @Value("${msika-flow.analytics.no-offer-min-requests:5}") int noOfferMinRequests,
                            @Value("${msika-flow.analytics.no-offer-window-hours:168}") long noOfferWindowHours) {
        this.selectionRepository = selectionRepository;
        this.requestRepository = requestRepository;
        this.offerRepository = offerRepository;
        this.snapshotRepository = snapshotRepository;
        this.recomputer = recomputer;
        this.findingService = findingService;
        this.objectMapper = objectMapper;
        this.concentrationWindowHours = concentrationWindowHours;
        this.concentrationThreshold = concentrationThreshold;
        this.concentrationMinVolume = concentrationMinVolume;
        this.rankingAuditLookbackHours = rankingAuditLookbackHours;
        this.noOfferRateThreshold = noOfferRateThreshold;
        this.noOfferMinRequests = noOfferMinRequests;
        this.noOfferWindowHours = noOfferWindowHours;
    }

    /** Run all fairness monitors; returns per-monitor new-finding/capture counts. */
    @Transactional
    public Map<String, Integer> sweep(OffsetDateTime now) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("rankingSnapshotsCaptured", captureRankingSnapshots());
        counts.put("concentrationWatch", sweepVendorConcentration(now));
        counts.put("rankingAudit", sweepRankingTransparency(now));
        counts.put("noOfferEquity", sweepNoOfferEquity(now));
        return counts;
    }

    // ── snapshot capture (best-effort, live requests) ─────────────────────

    /**
     * Capture/refresh the §11.1 ranked-position + label snapshot for every
     * live comparison (OFFERS_AVAILABLE / SELECTION_PENDING). Best-effort at
     * sweep cadence — commitment-time capture is the documented future seam;
     * a selection that commits between sweeps without a snapshot is flagged
     * RANKING_SNAPSHOT_MISSING by the audit rather than silently skipped.
     */
    int captureRankingSnapshots() {
        int captured = 0;
        List<MarketplaceRequestEntity> live = requestRepository.findByStatusIn(
                EnumSet.of(MarketplaceRequestStatus.OFFERS_AVAILABLE,
                        MarketplaceRequestStatus.SELECTION_PENDING));
        for (MarketplaceRequestEntity request : live) {
            List<FulfillmentOfferEntity> active =
                    offerRepository.findByRequestIdAndStatus(request.getRequestId(), OfferStatus.ACTIVE);
            if (active.isEmpty()) {
                continue;
            }
            for (RankedBecauseRecomputer.RankedRow row : recomputer.recompute(request, active)) {
                RankingSnapshotEntity snapshot = snapshotRepository
                        .findFirstByRequestIdAndOfferId(request.getRequestId(), row.offerId())
                        .orElseGet(() -> {
                            RankingSnapshotEntity fresh = new RankingSnapshotEntity();
                            fresh.setSnapshotId(UlidGenerator.generate());
                            fresh.setTenantId(request.getTenantId());
                            fresh.setRequestId(request.getRequestId());
                            fresh.setOfferId(row.offerId());
                            return fresh;
                        });
                snapshot.setPosition(row.position());
                snapshot.setRankedBecauseJson(toJsonArray(row.labels()));
                snapshot.setPriceTotal(row.priceTotal());
                snapshot.setCapturedAt(OffsetDateTime.now());
                snapshotRepository.save(snapshot);
                captured++;
            }
        }
        return captured;
    }

    // ── concentration watch (§21.2 equity family) ─────────────────────────

    int sweepVendorConcentration(OffsetDateTime now) {
        OffsetDateTime windowStart = now.minusHours(concentrationWindowHours);
        // tenant → vendor → commitment count
        Map<UUID, Map<String, Integer>> byTenant = new LinkedHashMap<>();
        for (SelectionEntity selection
                : selectionRepository.findByStatusAndCommittedAtAfter(SelectionStatus.COMMITTED, windowStart)) {
            Optional<FulfillmentOfferEntity> offer = offerRepository.findById(selection.getOfferId());
            if (offer.isEmpty()) {
                continue;
            }
            byTenant.computeIfAbsent(selection.getTenantId(), t -> new LinkedHashMap<>())
                    .merge(offer.get().getVendorId(), 1, Integer::sum);
        }
        int found = 0;
        for (Map.Entry<UUID, Map<String, Integer>> tenantEntry : byTenant.entrySet()) {
            int total = tenantEntry.getValue().values().stream().mapToInt(Integer::intValue).sum();
            if (total < concentrationMinVolume) {
                continue; // below-volume markets stay unflagged (small-n noise control)
            }
            for (Map.Entry<String, Integer> vendorEntry : tenantEntry.getValue().entrySet()) {
                BigDecimal share = BigDecimal.valueOf(vendorEntry.getValue())
                        .divide(BigDecimal.valueOf(total), MathContext.DECIMAL64);
                if (share.compareTo(concentrationThreshold) <= 0) {
                    continue;
                }
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("vendorId", vendorEntry.getKey());
                details.put("commitments", vendorEntry.getValue());
                details.put("marketTotal", total);
                details.put("share", share.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
                details.put("threshold", concentrationThreshold.toPlainString());
                details.put("windowHours", concentrationWindowHours);
                found += recordCount(new AnomalyFindingService.FindingCandidate(
                        tenantEntry.getKey(), AnomalyFindingType.CONCENTRATION_WATCH,
                        AnomalyFindingService.SEVERITY_WARN, "VENDOR", vendorEntry.getKey(),
                        "CONCENTRATION_WATCH:" + vendorEntry.getKey() + ":" + DAY.format(now.toLocalDate()),
                        windowStart, now, details));
            }
        }
        return found;
    }

    // ── ranking-transparency audit (§21.5) ────────────────────────────────

    int sweepRankingTransparency(OffsetDateTime now) {
        OffsetDateTime lookback = now.minusHours(rankingAuditLookbackHours);
        int found = 0;
        for (SelectionEntity selection
                : selectionRepository.findByStatusAndCommittedAtAfter(SelectionStatus.COMMITTED, lookback)) {
            Optional<MarketplaceRequestEntity> requestOpt = requestRepository.findById(selection.getRequestId());
            if (requestOpt.isEmpty()) {
                continue;
            }
            MarketplaceRequestEntity request = requestOpt.get();
            Optional<RankingSnapshotEntity> snapshot = snapshotRepository
                    .findFirstByRequestIdAndOfferId(selection.getRequestId(), selection.getOfferId());
            if (snapshot.isEmpty()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("selectionId", selection.getSelectionId());
                details.put("requestId", selection.getRequestId());
                details.put("offerId", selection.getOfferId());
                details.put("committedAt", String.valueOf(selection.getCommittedAt()));
                found += recordCount(new AnomalyFindingService.FindingCandidate(
                        selection.getTenantId(), AnomalyFindingType.RANKING_SNAPSHOT_MISSING,
                        AnomalyFindingService.SEVERITY_INFO, "SELECTION", selection.getSelectionId(),
                        "RANKING_SNAPSHOT_MISSING:" + selection.getSelectionId(),
                        selection.getCommittedAt(), now, details));
                continue;
            }
            List<FulfillmentOfferEntity> candidates = offerRepository
                    .findByRequestId(selection.getRequestId()).stream()
                    .filter(o -> AUDIT_CANDIDATE_STATUSES.contains(o.getStatus()))
                    .toList();
            Optional<RankedBecauseRecomputer.RankedRow> recomputed =
                    recomputer.recompute(request, candidates).stream()
                            .filter(r -> r.offerId().equals(selection.getOfferId()))
                            .findFirst();
            if (recomputed.isEmpty()) {
                continue; // offer vanished from candidate set — nothing deterministic to compare
            }
            Set<String> expected = parseLabels(snapshot.get().getRankedBecauseJson());
            Set<String> actual = new TreeSet<>(recomputed.get().labels());
            if (expected.equals(actual)) {
                continue;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("selectionId", selection.getSelectionId());
            details.put("requestId", selection.getRequestId());
            details.put("offerId", selection.getOfferId());
            details.put("capturedLabels", new ArrayList<>(expected));
            details.put("recomputedLabels", new ArrayList<>(actual));
            details.put("capturedPosition", snapshot.get().getPosition());
            details.put("recomputedPosition", recomputed.get().position());
            details.put("capturedAt", String.valueOf(snapshot.get().getCapturedAt()));
            found += recordCount(new AnomalyFindingService.FindingCandidate(
                    selection.getTenantId(), AnomalyFindingType.RANKING_DRIFT,
                    AnomalyFindingService.SEVERITY_HIGH, "SELECTION", selection.getSelectionId(),
                    "RANKING_DRIFT:" + selection.getSelectionId(),
                    snapshot.get().getCapturedAt(), now, details));
        }
        return found;
    }

    // ── no-offer equity (§21.2 marketplace-liquidity / §22.2 zone pattern) ─

    int sweepNoOfferEquity(OffsetDateTime now) {
        OffsetDateTime windowStart = now.minusHours(noOfferWindowHours);
        // tenant → zone → [published, failedNoOffers]
        Map<UUID, Map<String, int[]>> byTenant = new LinkedHashMap<>();
        for (MarketplaceRequestEntity request : requestRepository.findByCreatedAtAfter(windowStart)) {
            if (!PUBLISHED_STATUSES.contains(request.getStatus())) {
                continue;
            }
            String zone = request.getCoarseZone() == null || request.getCoarseZone().isBlank()
                    ? "UNZONED" : request.getCoarseZone();
            int[] counts = byTenant
                    .computeIfAbsent(request.getTenantId(), t -> new LinkedHashMap<>())
                    .computeIfAbsent(zone, z -> new int[2]);
            counts[0]++;
            if (request.getStatus() == MarketplaceRequestStatus.FAILED_NO_OFFERS) {
                counts[1]++;
            }
        }
        int found = 0;
        for (Map.Entry<UUID, Map<String, int[]>> tenantEntry : byTenant.entrySet()) {
            for (Map.Entry<String, int[]> zoneEntry : tenantEntry.getValue().entrySet()) {
                int published = zoneEntry.getValue()[0];
                int failed = zoneEntry.getValue()[1];
                if (published < noOfferMinRequests || failed == 0) {
                    continue;
                }
                BigDecimal rate = BigDecimal.valueOf(failed)
                        .divide(BigDecimal.valueOf(published), MathContext.DECIMAL64);
                if (rate.compareTo(noOfferRateThreshold) < 0) {
                    continue;
                }
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("coarseZone", zoneEntry.getKey());
                details.put("publishedRequests", published);
                details.put("failedNoOffers", failed);
                details.put("noOfferRate", rate.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
                details.put("threshold", noOfferRateThreshold.toPlainString());
                details.put("windowHours", noOfferWindowHours);
                found += recordCount(new AnomalyFindingService.FindingCandidate(
                        tenantEntry.getKey(), AnomalyFindingType.NO_OFFER_EQUITY,
                        AnomalyFindingService.SEVERITY_WARN, "ZONE", zoneEntry.getKey(),
                        "NO_OFFER_EQUITY:" + zoneEntry.getKey() + ":" + DAY.format(now.toLocalDate()),
                        windowStart, now, details));
            }
        }
        return found;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private int recordCount(AnomalyFindingService.FindingCandidate candidate) {
        return findingService.record(candidate).isPresent() ? 1 : 0;
    }

    private String toJsonArray(List<String> labels) {
        try {
            return objectMapper.writeValueAsString(labels);
        } catch (Exception e) {
            log.warn("Unserialisable label list {}: {}", labels, e.getMessage());
            return "[]";
        }
    }

    private Set<String> parseLabels(String json) {
        Set<String> labels = new TreeSet<>();
        try {
            JsonNode node = objectMapper.readTree(json);
            for (JsonNode label : node) {
                labels.add(label.asText());
            }
        } catch (Exception e) {
            log.warn("Unparseable ranked-because snapshot: {}", e.getMessage());
        }
        return labels;
    }
}
