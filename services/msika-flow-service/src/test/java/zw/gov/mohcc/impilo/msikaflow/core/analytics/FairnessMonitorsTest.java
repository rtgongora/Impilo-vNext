package zw.gov.mohcc.impilo.msikaflow.core.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.core.EligibilityService;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingType;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceProfile;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.SelectionStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.AnomalyFindingEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.RankingSnapshotEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.SelectionEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferLineRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.MarketplaceRequestRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.RankingSnapshotRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.SelectionRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.VendorProfileRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OF-B29 §21 — fairness monitors: concentration watch, ranking-transparency
 * audit (snapshot capture, drift, mandatory-snapshot enforcement), no-offer
 * equity by coarse zone. Positive + negative matrix and config thresholds.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FairnessMonitorsTest {

    @Mock private SelectionRepository selectionRepository;
    @Mock private MarketplaceRequestRepository requestRepository;
    @Mock private FulfillmentOfferRepository offerRepository;
    @Mock private RankingSnapshotRepository snapshotRepository;
    @Mock private FulfillmentOfferLineRepository offerLineRepository;
    @Mock private VendorProfileRepository vendorRepository;
    @Mock private EligibilityService eligibilityService;
    @Mock private AnomalyFindingService findingService;

    private final ObjectMapper mapper = new ObjectMapper();
    private FairnessMonitors monitors;

    private static final UUID TENANT = UUID.randomUUID();
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-23T12:00:00Z");

    @BeforeEach
    void setUp() {
        RankedBecauseRecomputer recomputer = new RankedBecauseRecomputer(
                offerLineRepository, vendorRepository, eligibilityService, mapper);
        monitors = new FairnessMonitors(selectionRepository, requestRepository, offerRepository,
                snapshotRepository, recomputer, findingService, mapper,
                168, new BigDecimal("0.50"), 10, 72, new BigDecimal("0.50"), 5, 168);
        when(findingService.record(any())).thenReturn(Optional.of(new AnomalyFindingEntity()));
        when(selectionRepository.findByStatusAndCommittedAtAfter(any(), any())).thenReturn(List.of());
        when(requestRepository.findByStatusIn(any())).thenReturn(List.of());
        when(requestRepository.findByCreatedAtAfter(any())).thenReturn(List.of());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepository.findFirstByRequestIdAndOfferId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(offerLineRepository.findByOfferId(anyString())).thenReturn(List.of());
        when(vendorRepository.findById(anyString())).thenReturn(Optional.empty());
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private MarketplaceRequestEntity request(String id, MarketplaceRequestStatus status, String zone) {
        MarketplaceRequestEntity request = new MarketplaceRequestEntity();
        request.setRequestId(id);
        request.setTenantId(TENANT);
        request.setProfile(MarketplaceProfile.MEDICATION);
        request.setStatus(status);
        request.setCoarseZone(zone);
        request.setPublishedLinesJson("{\"lines\":[{\"lineRef\":\"l1\",\"quantity\":2}]}");
        request.setCreatedAt(NOW.minusHours(10));
        when(requestRepository.findById(id)).thenReturn(Optional.of(request));
        return request;
    }

    private FulfillmentOfferEntity offer(String offerId, String requestId, String vendorId,
                                         OfferStatus status, String price, boolean coversLine) {
        FulfillmentOfferEntity offer = new FulfillmentOfferEntity();
        offer.setOfferId(offerId);
        offer.setTenantId(TENANT);
        offer.setRequestId(requestId);
        offer.setVendorId(vendorId);
        offer.setStatus(status);
        offer.setPriceTotal(new BigDecimal(price));
        offer.setCurrency("ZWG");
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));
        if (coversLine) {
            FulfillmentOfferLineEntity line = new FulfillmentOfferLineEntity();
            line.setOfferLineId("L-" + offerId);
            line.setTenantId(TENANT);
            line.setOfferId(offerId);
            line.setRequestLineRef("l1");
            line.setQuantity(2);
            when(offerLineRepository.findByOfferId(offerId)).thenReturn(List.of(line));
        }
        return offer;
    }

    private SelectionEntity committed(String id, String requestId, String offerId, OffsetDateTime at) {
        SelectionEntity selection = new SelectionEntity();
        selection.setSelectionId(id);
        selection.setTenantId(TENANT);
        selection.setRequestId(requestId);
        selection.setOfferId(offerId);
        selection.setStatus(SelectionStatus.COMMITTED);
        selection.setIdempotencyKey("idem-" + id);
        selection.setCommittedAt(at);
        return selection;
    }

    private List<AnomalyFindingService.FindingCandidate> capturedFindings() {
        ArgumentCaptor<AnomalyFindingService.FindingCandidate> captor =
                ArgumentCaptor.forClass(AnomalyFindingService.FindingCandidate.class);
        verify(findingService, atLeast(0)).record(captor.capture());
        return captor.getAllValues();
    }

    // ── snapshot capture ──────────────────────────────────────────────────

    @Test
    void capture_liveRequest_persistsPositionedLabelSnapshots() {
        MarketplaceRequestEntity live = request("R1", MarketplaceRequestStatus.OFFERS_AVAILABLE, "Z1");
        when(requestRepository.findByStatusIn(any())).thenReturn(List.of(live));
        FulfillmentOfferEntity complete = offer("O1", "R1", "VEND1", OfferStatus.ACTIVE, "100.00", true);
        FulfillmentOfferEntity cheapest = offer("O2", "R1", "VEND2", OfferStatus.ACTIVE, "80.00", false);
        when(offerRepository.findByRequestIdAndStatus("R1", OfferStatus.ACTIVE))
                .thenReturn(List.of(complete, cheapest));

        int captured = monitors.captureRankingSnapshots();

        assertEquals(2, captured);
        ArgumentCaptor<RankingSnapshotEntity> snaps = ArgumentCaptor.forClass(RankingSnapshotEntity.class);
        verify(snapshotRepository, times(2)).save(snaps.capture());
        RankingSnapshotEntity first = snaps.getAllValues().stream()
                .filter(s -> s.getOfferId().equals("O1")).findFirst().orElseThrow();
        // O1 covers the full line set → COMPLETE → position 1 despite higher price (§4.5 sort).
        assertEquals(1, first.getPosition());
        assertTrue(first.getRankedBecauseJson().contains("COMPLETE"));
        assertTrue(first.getRankedBecauseJson().contains("SUITABLE"));
        RankingSnapshotEntity second = snaps.getAllValues().stream()
                .filter(s -> s.getOfferId().equals("O2")).findFirst().orElseThrow();
        assertEquals(2, second.getPosition());
        assertTrue(second.getRankedBecauseJson().contains("CHEAPEST"));
    }

    // ── concentration watch ───────────────────────────────────────────────

    @Test
    void concentration_dominantVendorAboveThreshold_flagsThatVendorOnly() {
        List<SelectionEntity> commits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            offer("OA" + i, "R", "VEND-BIG", OfferStatus.COMMITTED, "10.00", false);
            commits.add(committed("SA" + i, "R", "OA" + i, NOW.minusHours(i + 1)));
        }
        for (int i = 0; i < 4; i++) {
            offer("OB" + i, "R", "VEND-SMALL", OfferStatus.COMMITTED, "10.00", false);
            commits.add(committed("SB" + i, "R", "OB" + i, NOW.minusHours(i + 1)));
        }
        when(selectionRepository.findByStatusAndCommittedAtAfter(
                eq(SelectionStatus.COMMITTED), eq(NOW.minusHours(168)))).thenReturn(commits);

        assertEquals(1, monitors.sweepVendorConcentration(NOW));
        AnomalyFindingService.FindingCandidate candidate = capturedFindings().get(0);
        assertEquals(AnomalyFindingType.CONCENTRATION_WATCH, candidate.type());
        assertEquals("VEND-BIG", candidate.subjectRef());
        assertEquals("0.6000", candidate.details().get("share"));
        assertEquals(10, candidate.details().get("marketTotal"));
    }

    @Test
    void concentration_belowMinVolume_staysUnflagged() {
        List<SelectionEntity> commits = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            offer("OA" + i, "R", "VEND-BIG", OfferStatus.COMMITTED, "10.00", false);
            commits.add(committed("SA" + i, "R", "OA" + i, NOW.minusHours(1)));
        }
        when(selectionRepository.findByStatusAndCommittedAtAfter(any(), any())).thenReturn(commits);

        assertEquals(0, monitors.sweepVendorConcentration(NOW));
        verify(findingService, never()).record(any());
    }

    @Test
    void concentration_shareAtThreshold_isNotAboveIt() {
        List<SelectionEntity> commits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            offer("OA" + i, "R", "VEND-A", OfferStatus.COMMITTED, "10.00", false);
            commits.add(committed("SA" + i, "R", "OA" + i, NOW.minusHours(1)));
        }
        for (int i = 0; i < 5; i++) {
            offer("OB" + i, "R", "VEND-B", OfferStatus.COMMITTED, "10.00", false);
            commits.add(committed("SB" + i, "R", "OB" + i, NOW.minusHours(1)));
        }
        when(selectionRepository.findByStatusAndCommittedAtAfter(any(), any())).thenReturn(commits);

        assertEquals(0, monitors.sweepVendorConcentration(NOW));
    }

    // ── ranking-transparency audit ────────────────────────────────────────

    @Test
    void rankingAudit_missingSnapshot_flagsMandatorySnapshotGap() {
        request("R1", MarketplaceRequestStatus.COMMITTED, "Z1");
        SelectionEntity selection = committed("S1", "R1", "O1", NOW.minusHours(2));
        when(selectionRepository.findByStatusAndCommittedAtAfter(
                eq(SelectionStatus.COMMITTED), eq(NOW.minusHours(72))))
                .thenReturn(List.of(selection));
        when(snapshotRepository.findFirstByRequestIdAndOfferId("R1", "O1"))
                .thenReturn(Optional.empty());

        assertEquals(1, monitors.sweepRankingTransparency(NOW));
        AnomalyFindingService.FindingCandidate candidate = capturedFindings().get(0);
        assertEquals(AnomalyFindingType.RANKING_SNAPSHOT_MISSING, candidate.type());
        assertEquals("S1", candidate.subjectRef());
    }

    @Test
    void rankingAudit_labelsDrifted_flagsRankingDrift() {
        request("R1", MarketplaceRequestStatus.COMMITTED, "Z1");
        SelectionEntity selection = committed("S1", "R1", "O1", NOW.minusHours(2));
        when(selectionRepository.findByStatusAndCommittedAtAfter(any(), any()))
                .thenReturn(List.of(selection));
        // Captured while O1 was the cheapest…
        RankingSnapshotEntity snapshot = new RankingSnapshotEntity();
        snapshot.setSnapshotId("SNAP1");
        snapshot.setTenantId(TENANT);
        snapshot.setRequestId("R1");
        snapshot.setOfferId("O1");
        snapshot.setPosition(1);
        snapshot.setRankedBecauseJson("[\"SUITABLE\",\"CHEAPEST\"]");
        snapshot.setCapturedAt(NOW.minusHours(3));
        when(snapshotRepository.findFirstByRequestIdAndOfferId("R1", "O1"))
                .thenReturn(Optional.of(snapshot));
        // …but the record now shows a cheaper competing offer: the shown basis no longer holds.
        FulfillmentOfferEntity committedOffer = offer("O1", "R1", "VEND1", OfferStatus.COMMITTED, "100.00", false);
        FulfillmentOfferEntity rival = offer("O2", "R1", "VEND2", OfferStatus.NOT_SELECTED, "50.00", false);
        when(offerRepository.findByRequestId("R1")).thenReturn(List.of(committedOffer, rival));

        assertEquals(1, monitors.sweepRankingTransparency(NOW));
        AnomalyFindingService.FindingCandidate candidate = capturedFindings().get(0);
        assertEquals(AnomalyFindingType.RANKING_DRIFT, candidate.type());
        assertEquals(AnomalyFindingService.SEVERITY_HIGH, candidate.severity());
        assertTrue(candidate.details().get("capturedLabels").toString().contains("CHEAPEST"));
        assertFalse(candidate.details().get("recomputedLabels").toString().contains("CHEAPEST"));
    }

    @Test
    void rankingAudit_labelsStable_staysClean() {
        request("R1", MarketplaceRequestStatus.COMMITTED, "Z1");
        SelectionEntity selection = committed("S1", "R1", "O1", NOW.minusHours(2));
        when(selectionRepository.findByStatusAndCommittedAtAfter(any(), any()))
                .thenReturn(List.of(selection));
        RankingSnapshotEntity snapshot = new RankingSnapshotEntity();
        snapshot.setSnapshotId("SNAP1");
        snapshot.setTenantId(TENANT);
        snapshot.setRequestId("R1");
        snapshot.setOfferId("O1");
        snapshot.setPosition(1);
        snapshot.setRankedBecauseJson("[\"CHEAPEST\",\"SUITABLE\"]");
        snapshot.setCapturedAt(NOW.minusHours(3));
        when(snapshotRepository.findFirstByRequestIdAndOfferId("R1", "O1"))
                .thenReturn(Optional.of(snapshot));
        FulfillmentOfferEntity committedOffer = offer("O1", "R1", "VEND1", OfferStatus.COMMITTED, "50.00", false);
        FulfillmentOfferEntity rival = offer("O2", "R1", "VEND2", OfferStatus.NOT_SELECTED, "100.00", false);
        when(offerRepository.findByRequestId("R1")).thenReturn(List.of(committedOffer, rival));

        assertEquals(0, monitors.sweepRankingTransparency(NOW));
        verify(findingService, never()).record(any());
    }

    // ── no-offer equity ───────────────────────────────────────────────────

    @Test
    void noOfferEquity_zoneAboveRateThreshold_flagsZone() {
        List<MarketplaceRequestEntity> requests = new ArrayList<>();
        // Z1: 5 published, 3 failed-no-offers → 60%
        for (int i = 0; i < 3; i++) {
            requests.add(request("RZ1F" + i, MarketplaceRequestStatus.FAILED_NO_OFFERS, "Z1"));
        }
        for (int i = 0; i < 2; i++) {
            requests.add(request("RZ1C" + i, MarketplaceRequestStatus.COMMITTED, "Z1"));
        }
        // Z3: 10 published, 1 failed → 10% (clean)
        requests.add(request("RZ3F", MarketplaceRequestStatus.FAILED_NO_OFFERS, "Z3"));
        for (int i = 0; i < 9; i++) {
            requests.add(request("RZ3C" + i, MarketplaceRequestStatus.COMMITTED, "Z3"));
        }
        when(requestRepository.findByCreatedAtAfter(eq(NOW.minusHours(168)))).thenReturn(requests);

        assertEquals(1, monitors.sweepNoOfferEquity(NOW));
        AnomalyFindingService.FindingCandidate candidate = capturedFindings().get(0);
        assertEquals(AnomalyFindingType.NO_OFFER_EQUITY, candidate.type());
        assertEquals("Z1", candidate.subjectRef());
        assertEquals("0.6000", candidate.details().get("noOfferRate"));
        assertEquals(5, candidate.details().get("publishedRequests"));
    }

    @Test
    void noOfferEquity_belowMinRequests_staysUnflagged() {
        List<MarketplaceRequestEntity> requests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            requests.add(request("RZ2F" + i, MarketplaceRequestStatus.FAILED_NO_OFFERS, "Z2"));
        }
        when(requestRepository.findByCreatedAtAfter(any())).thenReturn(requests);

        assertEquals(0, monitors.sweepNoOfferEquity(NOW));
        verify(findingService, never()).record(any());
    }

    @Test
    void noOfferEquity_draftRequestsDoNotCountAsPublished() {
        List<MarketplaceRequestEntity> requests = new ArrayList<>();
        // 3 failed + 2 DRAFT: published=3 (<5 min) → no finding despite 100% failure rate
        for (int i = 0; i < 3; i++) {
            requests.add(request("RZ4F" + i, MarketplaceRequestStatus.FAILED_NO_OFFERS, "Z4"));
        }
        for (int i = 0; i < 2; i++) {
            requests.add(request("RZ4D" + i, MarketplaceRequestStatus.DRAFT, "Z4"));
        }
        when(requestRepository.findByCreatedAtAfter(any())).thenReturn(requests);

        assertEquals(0, monitors.sweepNoOfferEquity(NOW));
    }

    @Test
    void noOfferEquity_lowerThresholdConfig_flipsCleanZoneToFlagged() {
        FairnessMonitors sensitive = new FairnessMonitors(selectionRepository, requestRepository,
                offerRepository, snapshotRepository,
                new RankedBecauseRecomputer(offerLineRepository, vendorRepository, eligibilityService, mapper),
                findingService, mapper,
                168, new BigDecimal("0.50"), 10, 72, new BigDecimal("0.10"), 5, 168);
        List<MarketplaceRequestEntity> requests = new ArrayList<>();
        requests.add(request("RZ3F", MarketplaceRequestStatus.FAILED_NO_OFFERS, "Z3"));
        for (int i = 0; i < 9; i++) {
            requests.add(request("RZ3C" + i, MarketplaceRequestStatus.COMMITTED, "Z3"));
        }
        when(requestRepository.findByCreatedAtAfter(any())).thenReturn(requests);

        assertEquals(1, sensitive.sweepNoOfferEquity(NOW));
    }

    // ── sweep aggregation ─────────────────────────────────────────────────

    @Test
    void sweep_reportsPerMonitorCounts() {
        var counts = monitors.sweep(NOW);
        assertEquals(0, counts.get("rankingSnapshotsCaptured"));
        assertEquals(0, counts.get("concentrationWatch"));
        assertEquals(0, counts.get("rankingAudit"));
        assertEquals(0, counts.get("noOfferEquity"));
    }
}
