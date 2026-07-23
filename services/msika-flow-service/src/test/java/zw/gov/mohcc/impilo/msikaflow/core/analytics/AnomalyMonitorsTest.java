package zw.gov.mohcc.impilo.msikaflow.core.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingType;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceProfile;
import zw.gov.mohcc.impilo.msikaflow.domain.ReservationStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.SelectionStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.AnomalyFindingEntity;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OF-B29 §13.3/§13.7 — positive + negative matrix for each deterministic
 * anomaly monitor, config-threshold behaviour, and the PII-fold of the
 * requester pattern key.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyMonitorsTest {

    @Mock private SelectionRepository selectionRepository;
    @Mock private MarketplaceRequestRepository requestRepository;
    @Mock private FulfillmentOfferRepository offerRepository;
    @Mock private FulfillmentOfferLineRepository offerLineRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AnomalyFindingService findingService;

    private AnomalyMonitors monitors;

    private static final UUID TENANT = UUID.randomUUID();
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-23T12:00:00Z");

    @BeforeEach
    void setUp() {
        monitors = new AnomalyMonitors(selectionRepository, requestRepository, offerRepository,
                offerLineRepository, reservationRepository, findingService,
                72, 48, 3, 24, 60);
        when(findingService.record(any())).thenReturn(Optional.of(new AnomalyFindingEntity()));
        when(selectionRepository.findByStatus(any())).thenReturn(List.of());
        when(selectionRepository.findByStatusAndCommittedAtBefore(any(), any())).thenReturn(List.of());
        when(selectionRepository.findByStatusAndCommittedAtAfter(any(), any())).thenReturn(List.of());
        when(reservationRepository.findByStatusAndExpiresAtBefore(any(), any())).thenReturn(List.of());
        when(offerLineRepository.findByOfferId(anyString())).thenReturn(List.of());
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private MarketplaceRequestEntity request(String id, MarketplaceProfile profile, String requestedBy) {
        MarketplaceRequestEntity request = new MarketplaceRequestEntity();
        request.setRequestId(id);
        request.setTenantId(TENANT);
        request.setProfile(profile);
        request.setRequestedBy(requestedBy);
        when(requestRepository.findById(id)).thenReturn(Optional.of(request));
        return request;
    }

    private SelectionEntity committedSelection(String id, String requestId, String offerId,
                                               String claimId, OffsetDateTime committedAt) {
        SelectionEntity selection = new SelectionEntity();
        selection.setSelectionId(id);
        selection.setTenantId(TENANT);
        selection.setRequestId(requestId);
        selection.setOfferId(offerId);
        selection.setStatus(SelectionStatus.COMMITTED);
        selection.setIdempotencyKey("idem-" + id);
        selection.setPrescriptionClaimId(claimId);
        selection.setCommittedAt(committedAt);
        return selection;
    }

    private FulfillmentOfferEntity offer(String offerId, String vendorId) {
        FulfillmentOfferEntity offer = new FulfillmentOfferEntity();
        offer.setOfferId(offerId);
        offer.setTenantId(TENANT);
        offer.setVendorId(vendorId);
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));
        return offer;
    }

    private AnomalyFindingService.FindingCandidate captureSingleFinding() {
        ArgumentCaptor<AnomalyFindingService.FindingCandidate> captor =
                ArgumentCaptor.forClass(AnomalyFindingService.FindingCandidate.class);
        verify(findingService).record(captor.capture());
        return captor.getValue();
    }

    // ── 1. claim-without-episode ──────────────────────────────────────────

    @Test
    void claimWithoutEpisode_medicationClaimNoProgressPastTtl_flags() {
        request("R1", MarketplaceProfile.MEDICATION, "actor-1");
        SelectionEntity selection = committedSelection("S1", "R1", "O1", "claim-1", NOW.minusHours(100));
        when(selectionRepository.findByStatusAndCommittedAtBefore(
                eq(SelectionStatus.COMMITTED), eq(NOW.minusHours(72))))
                .thenReturn(List.of(selection));

        assertEquals(1, monitors.sweepClaimWithoutEpisode(NOW));
        AnomalyFindingService.FindingCandidate candidate = captureSingleFinding();
        assertEquals(AnomalyFindingType.CLAIM_WITHOUT_EPISODE, candidate.type());
        assertEquals("SELECTION", candidate.subjectType());
        assertEquals("S1", candidate.subjectRef());
        assertEquals("CLAIM_WITHOUT_EPISODE:S1", candidate.dedupeKey());
    }

    @Test
    void claimWithoutEpisode_deliveryProgress_isClean() {
        request("R1", MarketplaceProfile.MEDICATION, "actor-1");
        SelectionEntity selection = committedSelection("S1", "R1", "O1", "claim-1", NOW.minusHours(100));
        selection.setDeliveryStatus("PICKED_UP");
        when(selectionRepository.findByStatusAndCommittedAtBefore(any(), any()))
                .thenReturn(List.of(selection));

        assertEquals(0, monitors.sweepClaimWithoutEpisode(NOW));
        verify(findingService, never()).record(any());
    }

    @Test
    void claimWithoutEpisode_consumedDuraHold_isClean() {
        request("R1", MarketplaceProfile.MEDICATION, "actor-1");
        SelectionEntity selection = committedSelection("S1", "R1", "O1", "claim-1", NOW.minusHours(100));
        FulfillmentOfferLineEntity line = new FulfillmentOfferLineEntity();
        line.setOfferLineId("L1");
        line.setDuraReservationRef("dura-1");
        when(offerLineRepository.findByOfferId("O1")).thenReturn(List.of(line));
        ReservationEntity projection = new ReservationEntity();
        projection.setStatus(ReservationStatus.CONSUMED);
        when(reservationRepository.findFirstByReservationRef("dura-1"))
                .thenReturn(Optional.of(projection));
        when(selectionRepository.findByStatusAndCommittedAtBefore(any(), any()))
                .thenReturn(List.of(selection));

        assertEquals(0, monitors.sweepClaimWithoutEpisode(NOW));
    }

    @Test
    void claimWithoutEpisode_nonMedicationProfile_isOutOfScope() {
        request("R1", MarketplaceProfile.DIAGNOSTICS, "actor-1");
        SelectionEntity selection = committedSelection("S1", "R1", "O1", "claim-1", NOW.minusHours(100));
        when(selectionRepository.findByStatusAndCommittedAtBefore(any(), any()))
                .thenReturn(List.of(selection));

        assertEquals(0, monitors.sweepClaimWithoutEpisode(NOW));
    }

    @Test
    void claimWithoutEpisode_respectsConfiguredTtlCutoff() {
        monitors.sweepClaimWithoutEpisode(NOW);
        verify(selectionRepository).findByStatusAndCommittedAtBefore(
                SelectionStatus.COMMITTED, NOW.minusHours(72));
    }

    // ── 2. commitment-without-claim ───────────────────────────────────────

    @Test
    void commitmentWithoutClaim_medicationNoClaim_flagsHigh() {
        request("R1", MarketplaceProfile.MEDICATION, "actor-1");
        SelectionEntity selection = committedSelection("S1", "R1", "O1", null, NOW.minusHours(1));
        when(selectionRepository.findByStatus(SelectionStatus.COMMITTED)).thenReturn(List.of(selection));

        assertEquals(1, monitors.sweepCommitmentWithoutClaim(NOW));
        AnomalyFindingService.FindingCandidate candidate = captureSingleFinding();
        assertEquals(AnomalyFindingType.COMMITMENT_WITHOUT_CLAIM, candidate.type());
        assertEquals(AnomalyFindingService.SEVERITY_HIGH, candidate.severity());
    }

    @Test
    void commitmentWithoutClaim_claimPresent_isClean() {
        request("R1", MarketplaceProfile.MEDICATION, "actor-1");
        SelectionEntity selection = committedSelection("S1", "R1", "O1", "claim-1", NOW.minusHours(1));
        when(selectionRepository.findByStatus(SelectionStatus.COMMITTED)).thenReturn(List.of(selection));

        assertEquals(0, monitors.sweepCommitmentWithoutClaim(NOW));
    }

    @Test
    void commitmentWithoutClaim_serviceProfile_isOutOfScope() {
        request("R1", MarketplaceProfile.SERVICE, "actor-1");
        SelectionEntity selection = committedSelection("S1", "R1", "O1", null, NOW.minusHours(1));
        when(selectionRepository.findByStatus(SelectionStatus.COMMITTED)).thenReturn(List.of(selection));

        assertEquals(0, monitors.sweepCommitmentWithoutClaim(NOW));
    }

    // ── 3. stalled commitment ─────────────────────────────────────────────

    @Test
    void stalledCommitment_noProgressPastWindow_flags() {
        request("R1", MarketplaceProfile.SERVICE, "actor-1");
        SelectionEntity selection = committedSelection("S1", "R1", "O1", null, NOW.minusHours(72));
        when(selectionRepository.findByStatusAndCommittedAtBefore(
                eq(SelectionStatus.COMMITTED), eq(NOW.minusHours(48))))
                .thenReturn(List.of(selection));

        assertEquals(1, monitors.sweepStalledCommitments(NOW));
        assertEquals(AnomalyFindingType.STALLED_COMMITMENT, captureSingleFinding().type());
    }

    @Test
    void stalledCommitment_dispatchedDelivery_isProgress() {
        SelectionEntity selection = committedSelection("S1", "R1", "O1", null, NOW.minusHours(72));
        selection.setDispatchStatus("DISPATCHED");
        when(selectionRepository.findByStatusAndCommittedAtBefore(any(), any()))
                .thenReturn(List.of(selection));

        assertEquals(0, monitors.sweepStalledCommitments(NOW));
    }

    // ── 4. rapid-repeat wins ──────────────────────────────────────────────

    @Test
    void rapidRepeatWin_thresholdReachedSameVendorSameRequester_flagsOnceWithHashedKey() {
        request("R1", MarketplaceProfile.MEDICATION, "health-id-123");
        request("R2", MarketplaceProfile.MEDICATION, "health-id-123");
        request("R3", MarketplaceProfile.MEDICATION, "health-id-123");
        offer("O1", "VEND1");
        offer("O2", "VEND1");
        offer("O3", "VEND1");
        when(selectionRepository.findByStatusAndCommittedAtAfter(
                eq(SelectionStatus.COMMITTED), eq(NOW.minusHours(24))))
                .thenReturn(List.of(
                        committedSelection("S1", "R1", "O1", "c1", NOW.minusHours(3)),
                        committedSelection("S2", "R2", "O2", "c2", NOW.minusHours(2)),
                        committedSelection("S3", "R3", "O3", "c3", NOW.minusHours(1))));

        assertEquals(1, monitors.sweepRapidRepeatWins(NOW));
        AnomalyFindingService.FindingCandidate candidate = captureSingleFinding();
        assertEquals(AnomalyFindingType.RAPID_REPEAT_WIN, candidate.type());
        assertEquals("VEND1", candidate.subjectRef());
        assertEquals(3, candidate.details().get("winCount"));
        // Requester identity never appears raw — only the opaque fold.
        String detailsText = candidate.details().toString();
        assertFalse(detailsText.contains("health-id-123"));
        assertEquals(AnomalyMonitors.opaqueHash("health-id-123"),
                candidate.details().get("requesterPatternKey"));
        assertFalse(candidate.dedupeKey().contains("health-id-123"));
    }

    @Test
    void rapidRepeatWin_belowThreshold_isClean() {
        request("R1", MarketplaceProfile.MEDICATION, "health-id-123");
        request("R2", MarketplaceProfile.MEDICATION, "health-id-123");
        offer("O1", "VEND1");
        offer("O2", "VEND1");
        when(selectionRepository.findByStatusAndCommittedAtAfter(any(), any()))
                .thenReturn(List.of(
                        committedSelection("S1", "R1", "O1", "c1", NOW.minusHours(3)),
                        committedSelection("S2", "R2", "O2", "c2", NOW.minusHours(2))));

        assertEquals(0, monitors.sweepRapidRepeatWins(NOW));
    }

    @Test
    void rapidRepeatWin_differentVendors_isClean() {
        request("R1", MarketplaceProfile.MEDICATION, "health-id-123");
        request("R2", MarketplaceProfile.MEDICATION, "health-id-123");
        request("R3", MarketplaceProfile.MEDICATION, "health-id-123");
        offer("O1", "VEND1");
        offer("O2", "VEND2");
        offer("O3", "VEND3");
        when(selectionRepository.findByStatusAndCommittedAtAfter(any(), any()))
                .thenReturn(List.of(
                        committedSelection("S1", "R1", "O1", "c1", NOW.minusHours(3)),
                        committedSelection("S2", "R2", "O2", "c2", NOW.minusHours(2)),
                        committedSelection("S3", "R3", "O3", "c3", NOW.minusHours(1))));

        assertEquals(0, monitors.sweepRapidRepeatWins(NOW));
    }

    @Test
    void rapidRepeatWin_lowerConfiguredThreshold_flipsNegativeToPositive() {
        AnomalyMonitors strict = new AnomalyMonitors(selectionRepository, requestRepository,
                offerRepository, offerLineRepository, reservationRepository, findingService,
                72, 48, 2, 24, 60);
        request("R1", MarketplaceProfile.MEDICATION, "health-id-123");
        request("R2", MarketplaceProfile.MEDICATION, "health-id-123");
        offer("O1", "VEND1");
        offer("O2", "VEND1");
        when(selectionRepository.findByStatusAndCommittedAtAfter(any(), any()))
                .thenReturn(List.of(
                        committedSelection("S1", "R1", "O1", "c1", NOW.minusHours(3)),
                        committedSelection("S2", "R2", "O2", "c2", NOW.minusHours(2))));

        assertEquals(1, strict.sweepRapidRepeatWins(NOW));
    }

    // ── 5. reservation leak ───────────────────────────────────────────────

    @Test
    void reservationLeak_liveProjectionPastTtlPlusGrace_flags() {
        ReservationEntity leaked = new ReservationEntity();
        leaked.setId("01RESAAAAAAAAAAAAAAAAAAAAA");
        leaked.setOrderId("oros-1");
        leaked.setLineId("L1");
        leaked.setSystem("INVENTORY");
        leaked.setStatus(ReservationStatus.CONFIRMED);
        leaked.setReservationRef("dura-9");
        leaked.setExpiresAt(NOW.minusHours(3));
        when(reservationRepository.findByStatusAndExpiresAtBefore(
                eq(ReservationStatus.CONFIRMED), eq(NOW.minusMinutes(60))))
                .thenReturn(List.of(leaked));
        FulfillmentOfferLineEntity line = new FulfillmentOfferLineEntity();
        line.setOfferLineId("L1");
        line.setTenantId(TENANT);
        line.setDuraReservationRef("dura-9");
        when(offerLineRepository.findByDuraReservationRef("dura-9")).thenReturn(List.of(line));

        assertEquals(1, monitors.sweepReservationLeaks(NOW));
        AnomalyFindingService.FindingCandidate candidate = captureSingleFinding();
        assertEquals(AnomalyFindingType.RESERVATION_LEAK, candidate.type());
        assertEquals(TENANT, candidate.tenantId());
        assertEquals("RESERVATION_LEAK:01RESAAAAAAAAAAAAAAAAAAAAA", candidate.dedupeKey());
    }

    @Test
    void reservationLeak_noMarketplaceLine_isSkippedNotMislabeled() {
        ReservationEntity legacy = new ReservationEntity();
        legacy.setId("01RESBBBBBBBBBBBBBBBBBBBBB");
        legacy.setStatus(ReservationStatus.PENDING);
        legacy.setReservationRef("legacy-ref");
        legacy.setExpiresAt(NOW.minusHours(3));
        when(reservationRepository.findByStatusAndExpiresAtBefore(
                eq(ReservationStatus.PENDING), any())).thenReturn(List.of(legacy));
        when(offerLineRepository.findByDuraReservationRef("legacy-ref")).thenReturn(List.of());

        assertEquals(0, monitors.sweepReservationLeaks(NOW));
        verify(findingService, never()).record(any());
    }

    // ── sweep aggregation ─────────────────────────────────────────────────

    @Test
    void sweep_reportsPerMonitorCounts() {
        var counts = monitors.sweep(NOW);
        assertEquals(0, counts.get("claimWithoutEpisode"));
        assertEquals(0, counts.get("commitmentWithoutClaim"));
        assertEquals(0, counts.get("stalledCommitments"));
        assertEquals(0, counts.get("rapidRepeatWins"));
        assertEquals(0, counts.get("reservationLeaks"));
    }
}
