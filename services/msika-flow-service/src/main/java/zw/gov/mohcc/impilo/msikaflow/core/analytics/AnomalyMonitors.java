package zw.gov.mohcc.impilo.msikaflow.core.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingType;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceProfile;
import zw.gov.mohcc.impilo.msikaflow.domain.ReservationStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.SelectionStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.ReservationEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.SelectionEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferLineRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.MarketplaceRequestRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.ReservationRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.SelectionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OF-B29 §13.3/§13.7 — deterministic anomaly monitors over msika-flow's own
 * marketplace truth (the marketplace-side complement of pharmacy's
 * dispense-episode sweeps). Read-only over marketplace state; the only writes
 * are accountable findings via {@link AnomalyFindingService}.
 *
 * <p>Monitors (each rule + config threshold, no statistical models):</p>
 * <ol>
 *   <li><b>CLAIM_WITHOUT_EPISODE</b> — committed medication selection holding a
 *       prescription claim with no observable dispense progress past the claim
 *       TTL (§13.3.1 marketplace-side view).</li>
 *   <li><b>COMMITMENT_WITHOUT_CLAIM</b> — committed medication selection with
 *       no claim recorded at all (fulfilment outside the token discipline).</li>
 *   <li><b>STALLED_COMMITMENT</b> — any committed selection with no
 *       dispatch/dispense/delivery progress past the stall window.</li>
 *   <li><b>RAPID_REPEAT_WIN</b> — same vendor wins repeatedly for the same
 *       requester within a short window (T-pattern); the requester enters the
 *       pattern key only as an opaque SHA-256 fold, never as an identifier.</li>
 *   <li><b>RESERVATION_LEAK</b> — reservation projection still live past
 *       TTL + grace (hold leaked or release/consume event missed).</li>
 * </ol>
 *
 * <p>Dispense-progress signal (marketplace-side truth only, documented):
 * a delivery-status write-back from Nhume, a DISPATCHED delivery task, or a
 * DURA reservation projection flipped CONSUMED (ledger ISSUE consumed the
 * hold — the marketplace echo of a dispense). Pharmacy-side episode truth is
 * a future cross-service correlation feed, not read here.</p>
 */
@Service
public class AnomalyMonitors {

    private static final Logger log = LoggerFactory.getLogger(AnomalyMonitors.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final SelectionRepository selectionRepository;
    private final MarketplaceRequestRepository requestRepository;
    private final FulfillmentOfferRepository offerRepository;
    private final FulfillmentOfferLineRepository offerLineRepository;
    private final ReservationRepository reservationRepository;
    private final AnomalyFindingService findingService;

    private final long claimTtlHours;
    private final long stallWindowHours;
    private final int repeatWinThreshold;
    private final long repeatWindowHours;
    private final long reservationGraceMinutes;

    public AnomalyMonitors(SelectionRepository selectionRepository,
                           MarketplaceRequestRepository requestRepository,
                           FulfillmentOfferRepository offerRepository,
                           FulfillmentOfferLineRepository offerLineRepository,
                           ReservationRepository reservationRepository,
                           AnomalyFindingService findingService,
                           @Value("${msika-flow.analytics.claim-ttl-hours:72}") long claimTtlHours,
                           @Value("${msika-flow.analytics.stall-window-hours:48}") long stallWindowHours,
                           @Value("${msika-flow.analytics.repeat-win-threshold:3}") int repeatWinThreshold,
                           @Value("${msika-flow.analytics.repeat-window-hours:24}") long repeatWindowHours,
                           @Value("${msika-flow.analytics.reservation-grace-minutes:60}") long reservationGraceMinutes) {
        this.selectionRepository = selectionRepository;
        this.requestRepository = requestRepository;
        this.offerRepository = offerRepository;
        this.offerLineRepository = offerLineRepository;
        this.reservationRepository = reservationRepository;
        this.findingService = findingService;
        this.claimTtlHours = claimTtlHours;
        this.stallWindowHours = stallWindowHours;
        this.repeatWinThreshold = repeatWinThreshold;
        this.repeatWindowHours = repeatWindowHours;
        this.reservationGraceMinutes = reservationGraceMinutes;
    }

    /** Run all anomaly monitors; returns new-finding count per monitor (log/ops truth). */
    @Transactional
    public Map<String, Integer> sweep(OffsetDateTime now) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("claimWithoutEpisode", sweepClaimWithoutEpisode(now));
        counts.put("commitmentWithoutClaim", sweepCommitmentWithoutClaim(now));
        counts.put("stalledCommitments", sweepStalledCommitments(now));
        counts.put("rapidRepeatWins", sweepRapidRepeatWins(now));
        counts.put("reservationLeaks", sweepReservationLeaks(now));
        return counts;
    }

    // ── 1. claim-without-episode (§13.3.1, medication, marketplace-side) ──

    int sweepClaimWithoutEpisode(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusHours(claimTtlHours);
        int found = 0;
        for (SelectionEntity selection
                : selectionRepository.findByStatusAndCommittedAtBefore(SelectionStatus.COMMITTED, cutoff)) {
            if (selection.getPrescriptionClaimId() == null || selection.getPrescriptionClaimId().isBlank()) {
                continue; // claimless case is monitor 2's fact
            }
            Optional<MarketplaceRequestEntity> request = requestRepository.findById(selection.getRequestId());
            if (request.isEmpty() || request.get().getProfile() != MarketplaceProfile.MEDICATION) {
                continue;
            }
            if (hasDispenseProgress(selection)) {
                continue;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("selectionId", selection.getSelectionId());
            details.put("requestId", selection.getRequestId());
            details.put("offerId", selection.getOfferId());
            details.put("prescriptionClaimId", selection.getPrescriptionClaimId());
            details.put("committedAt", String.valueOf(selection.getCommittedAt()));
            details.put("claimTtlHours", claimTtlHours);
            found += recordCount(new AnomalyFindingService.FindingCandidate(
                    selection.getTenantId(), AnomalyFindingType.CLAIM_WITHOUT_EPISODE,
                    AnomalyFindingService.SEVERITY_WARN, "SELECTION", selection.getSelectionId(),
                    "CLAIM_WITHOUT_EPISODE:" + selection.getSelectionId(),
                    selection.getCommittedAt(), now, details));
        }
        return found;
    }

    // ── 2. commitment-without-claim (medication token discipline) ─────────

    int sweepCommitmentWithoutClaim(OffsetDateTime now) {
        int found = 0;
        for (SelectionEntity selection : selectionRepository.findByStatus(SelectionStatus.COMMITTED)) {
            if (selection.getPrescriptionClaimId() != null && !selection.getPrescriptionClaimId().isBlank()) {
                continue;
            }
            Optional<MarketplaceRequestEntity> request = requestRepository.findById(selection.getRequestId());
            if (request.isEmpty() || request.get().getProfile() != MarketplaceProfile.MEDICATION) {
                continue;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("selectionId", selection.getSelectionId());
            details.put("requestId", selection.getRequestId());
            details.put("offerId", selection.getOfferId());
            details.put("profile", MarketplaceProfile.MEDICATION.name());
            found += recordCount(new AnomalyFindingService.FindingCandidate(
                    selection.getTenantId(), AnomalyFindingType.COMMITMENT_WITHOUT_CLAIM,
                    AnomalyFindingService.SEVERITY_HIGH, "SELECTION", selection.getSelectionId(),
                    "COMMITMENT_WITHOUT_CLAIM:" + selection.getSelectionId(),
                    selection.getCommittedAt(), now, details));
        }
        return found;
    }

    // ── 3. stalled commitment (any profile, no progress past window) ──────

    int sweepStalledCommitments(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusHours(stallWindowHours);
        int found = 0;
        for (SelectionEntity selection
                : selectionRepository.findByStatusAndCommittedAtBefore(SelectionStatus.COMMITTED, cutoff)) {
            if (hasDispenseProgress(selection)) {
                continue;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("selectionId", selection.getSelectionId());
            details.put("requestId", selection.getRequestId());
            details.put("offerId", selection.getOfferId());
            details.put("dispatchStatus", String.valueOf(selection.getDispatchStatus()));
            details.put("committedAt", String.valueOf(selection.getCommittedAt()));
            details.put("stallWindowHours", stallWindowHours);
            found += recordCount(new AnomalyFindingService.FindingCandidate(
                    selection.getTenantId(), AnomalyFindingType.STALLED_COMMITMENT,
                    AnomalyFindingService.SEVERITY_WARN, "SELECTION", selection.getSelectionId(),
                    "STALLED_COMMITMENT:" + selection.getSelectionId(),
                    selection.getCommittedAt(), now, details));
        }
        return found;
    }

    // ── 4. rapid-repeat wins (same vendor, same requester, short window) ──

    int sweepRapidRepeatWins(OffsetDateTime now) {
        OffsetDateTime windowStart = now.minusHours(repeatWindowHours);
        // key: tenant|vendor|requesterHash → selection ids
        Map<String, List<SelectionEntity>> wins = new LinkedHashMap<>();
        Map<String, String> vendorByKey = new LinkedHashMap<>();
        for (SelectionEntity selection
                : selectionRepository.findByStatusAndCommittedAtAfter(SelectionStatus.COMMITTED, windowStart)) {
            Optional<MarketplaceRequestEntity> request = requestRepository.findById(selection.getRequestId());
            if (request.isEmpty() || request.get().getRequestedBy() == null
                    || request.get().getRequestedBy().isBlank()) {
                continue;
            }
            Optional<FulfillmentOfferEntity> offer = offerRepository.findById(selection.getOfferId());
            if (offer.isEmpty()) {
                continue;
            }
            String requesterHash = opaqueHash(request.get().getRequestedBy());
            String key = selection.getTenantId() + "|" + offer.get().getVendorId() + "|" + requesterHash;
            wins.computeIfAbsent(key, k -> new ArrayList<>()).add(selection);
            vendorByKey.put(key, offer.get().getVendorId());
        }
        int found = 0;
        for (Map.Entry<String, List<SelectionEntity>> entry : wins.entrySet()) {
            List<SelectionEntity> selections = entry.getValue();
            if (selections.size() < repeatWinThreshold) {
                continue;
            }
            String vendorId = vendorByKey.get(entry.getKey());
            String requesterHash = entry.getKey().substring(entry.getKey().lastIndexOf('|') + 1);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("vendorId", vendorId);
            details.put("winCount", selections.size());
            details.put("threshold", repeatWinThreshold);
            details.put("windowHours", repeatWindowHours);
            details.put("selectionIds", selections.stream().map(SelectionEntity::getSelectionId).toList());
            // requester identity never leaves the hash
            details.put("requesterPatternKey", requesterHash);
            found += recordCount(new AnomalyFindingService.FindingCandidate(
                    selections.get(0).getTenantId(), AnomalyFindingType.RAPID_REPEAT_WIN,
                    AnomalyFindingService.SEVERITY_WARN, "VENDOR", vendorId,
                    "RAPID_REPEAT_WIN:" + vendorId + ":" + requesterHash + ":" + DAY.format(now.toLocalDate()),
                    windowStart, now, details));
        }
        return found;
    }

    // ── 5. reservation leak (projection live past TTL + grace) ────────────

    int sweepReservationLeaks(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusMinutes(reservationGraceMinutes);
        int found = 0;
        List<ReservationEntity> leaked = new ArrayList<>();
        leaked.addAll(reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.PENDING, cutoff));
        leaked.addAll(reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.CONFIRMED, cutoff));
        for (ReservationEntity reservation : leaked) {
            // Tenant rides the marketplace offer line the DURA ref is pinned to.
            // Rows with no marketplace line (legacy cart-order projections) are
            // out of this monitor's SoR view — skipped, not silently mislabeled.
            List<FulfillmentOfferLineEntity> lines = reservation.getReservationRef() == null
                    ? List.of()
                    : offerLineRepository.findByDuraReservationRef(reservation.getReservationRef());
            if (lines.isEmpty()) {
                log.debug("Reservation {} leak candidate has no marketplace line — legacy projection, skipped",
                        reservation.getId());
                continue;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("reservationId", reservation.getId());
            details.put("reservationRef", reservation.getReservationRef());
            details.put("status", reservation.getStatus().name());
            details.put("expiresAt", String.valueOf(reservation.getExpiresAt()));
            details.put("graceMinutes", reservationGraceMinutes);
            details.put("offerLineId", lines.get(0).getOfferLineId());
            found += recordCount(new AnomalyFindingService.FindingCandidate(
                    lines.get(0).getTenantId(), AnomalyFindingType.RESERVATION_LEAK,
                    AnomalyFindingService.SEVERITY_HIGH, "RESERVATION", reservation.getId(),
                    "RESERVATION_LEAK:" + reservation.getId(),
                    reservation.getExpiresAt(), now, details));
        }
        return found;
    }

    // ── shared signals ────────────────────────────────────────────────────

    /**
     * Marketplace-side dispense/fulfilment progress: Nhume delivery status
     * written back, a delivery task DISPATCHED, or any of the selection's
     * offer-line DURA holds consumed by a ledger ISSUE.
     */
    boolean hasDispenseProgress(SelectionEntity selection) {
        if (selection.getDeliveryStatus() != null && !selection.getDeliveryStatus().isBlank()) {
            return true;
        }
        if ("DISPATCHED".equals(selection.getDispatchStatus())) {
            return true;
        }
        for (FulfillmentOfferLineEntity line : offerLineRepository.findByOfferId(selection.getOfferId())) {
            if (line.getDuraReservationRef() == null) {
                continue;
            }
            boolean consumed = reservationRepository
                    .findFirstByReservationRef(line.getDuraReservationRef())
                    .map(r -> r.getStatus() == ReservationStatus.CONSUMED)
                    .orElse(false);
            if (consumed) {
                return true;
            }
        }
        return false;
    }

    private int recordCount(AnomalyFindingService.FindingCandidate candidate) {
        return findingService.record(candidate).isPresent() ? 1 : 0;
    }

    /** Opaque, non-reversible fold of the requester id for pattern keys (PII-free doctrine). */
    static String opaqueHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed, 0, 6); // 12 hex chars
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
