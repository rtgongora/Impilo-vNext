package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.integration.InventoryClient;
import zw.gov.mohcc.impilo.msikaflow.integration.MushexClient;
import zw.gov.mohcc.impilo.msikaflow.integration.OrosClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OF-B14 — split fulfilment (§8.6.4, journey #45): combination validation
 * (overlap refusal, duplicate/not-active members), serial per-member commits
 * through the REAL {@link CommitmentService} (composition, no bespoke split
 * machine), per-member compensation isolation (a failing member never touches a
 * committed sibling — committed members STAND), the honest PARTIAL_COMMIT
 * rollup, the request-level coverage rollup (COMMITTED only when every line is
 * covered), replay idempotency, and the RC-1 per-line-coverage extension.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SplitSelectionCoordinatorTest {

    @Mock private MarketplaceRequestRepository requestRepository;
    @Mock private FulfillmentOfferRepository offerRepository;
    @Mock private FulfillmentOfferLineRepository offerLineRepository;
    @Mock private SelectionRepository selectionRepository;
    @Mock private VendorProfileRepository vendorRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private EligibilityService eligibilityService;
    @Mock private InventoryClient inventoryClient;
    @Mock private OrosClient orosClient;
    @Mock private OfferFinancialsService offerFinancialsService;
    @Mock private MushexClient mushexClient;
    @Mock private FulfilmentDispatchService fulfilmentDispatchService;
    @Mock private MarketplaceRequestService marketplaceRequestService;

    private final ObjectMapper mapper = new ObjectMapper();
    private CommitmentService commitmentService;
    private SplitSelectionCoordinator coordinator;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String REQUEST_ID = "01REQSPLITAAAAAAAAAAAAAAAA";
    private static final String OFFER_A = "01OFFSPLITAAAAAAAAAAAAAAAA"; // covers L1
    private static final String OFFER_B = "01OFFSPLITBBBBBBBBBBBBBBBB"; // covers L2
    private static final String OFFER_C = "01OFFSPLITCCCCCCCCCCCCCCCC"; // covers L1+L2 (full)
    private static final String VENDOR_A = "01VENSPLITAAAAAAAAAAAAAAAA";
    private static final String VENDOR_B = "01VENSPLITBBBBBBBBBBBBBBBB";

    private MarketplaceRequestEntity request;
    private FulfillmentOfferEntity offerA;
    private FulfillmentOfferEntity offerB;
    private FulfillmentOfferEntity offerC;
    private final Map<String, SelectionEntity> selectionStore = new LinkedHashMap<>();
    private final List<EventOutboxEntity> outboxEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        commitmentService = new CommitmentService(requestRepository, offerRepository,
                offerLineRepository, selectionRepository, vendorRepository, reservationRepository,
                outboxRepository, eligibilityService, inventoryClient, orosClient,
                offerFinancialsService, mushexClient, fulfilmentDispatchService, mapper, 24);
        coordinator = new SplitSelectionCoordinator(requestRepository, offerRepository,
                offerLineRepository, selectionRepository, outboxRepository, commitmentService,
                marketplaceRequestService, mapper);

        when(fulfilmentDispatchService.dispatchOnCommit(any(), any(), any(), any(), any()))
                .thenReturn(new FulfilmentDispatchService.DispatchOutcome(
                        FulfilmentDispatchService.DISPATCH_NOT_REQUIRED, null,
                        "no pathway election — PICKUP assumed (fail-closed)"));

        request = new MarketplaceRequestEntity();
        request.setRequestId(REQUEST_ID);
        request.setTenantId(TENANT);
        request.setOrosOrderId("OROS-SPLIT-1");
        request.setOrosOrderVersionId("OV-1");
        request.setProfile(MarketplaceProfile.MEDICATION); // no token => step 6 skipped
        request.setPublicationMode(PublicationMode.INVITED);
        request.setStatus(MarketplaceRequestStatus.OFFERS_AVAILABLE);
        request.setPublishedLinesJson("{\"lines\":["
                + "{\"lineRef\":\"L1\",\"itemCode\":\"ZIBO-A\",\"quantity\":2},"
                + "{\"lineRef\":\"L2\",\"itemCode\":\"ZIBO-B\",\"quantity\":1}]}");

        offerA = offer(OFFER_A, VENDOR_A, "60.00");
        offerB = offer(OFFER_B, VENDOR_B, "25.00");
        offerC = offer(OFFER_C, VENDOR_A, "80.00");

        stubOfferLines(OFFER_A, line(OFFER_A, "L1", "ZIBO-A", 2));
        stubOfferLines(OFFER_B, line(OFFER_B, "L2", "ZIBO-B", 1));
        stubOfferLines(OFFER_C, line(OFFER_C, "L1", "ZIBO-A", 2), line(OFFER_C, "L2", "ZIBO-B", 1));

        when(requestRepository.findByRequestIdAndTenantId(REQUEST_ID, TENANT))
                .thenReturn(Optional.of(request));
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of(offerA, offerB));
        when(offerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.findFirstByReservationRef(anyString())).thenReturn(Optional.empty());
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(inv -> {
            outboxEvents.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        // Cumulative selection store — serial member commits see their siblings.
        when(selectionRepository.save(any(SelectionEntity.class))).thenAnswer(inv -> {
            SelectionEntity s = inv.getArgument(0);
            selectionStore.put(s.getSelectionId(), s);
            return s;
        });
        when(selectionRepository.findByRequestId(REQUEST_ID)).thenAnswer(inv ->
                selectionStore.values().stream()
                        .filter(s -> REQUEST_ID.equals(s.getRequestId())).toList());
        when(selectionRepository.findFirstByTenantIdAndIdempotencyKey(any(), anyString()))
                .thenAnswer(inv -> selectionStore.values().stream()
                        .filter(s -> inv.getArgument(1).equals(s.getIdempotencyKey()))
                        .findFirst());
        when(selectionRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(selectionStore.get(inv.getArgument(0))));

        VendorProfileEntity vendorA = vendor(VENDOR_A);
        VendorProfileEntity vendorB = vendor(VENDOR_B);
        when(vendorRepository.findById(VENDOR_A)).thenReturn(Optional.of(vendorA));
        when(vendorRepository.findById(VENDOR_B)).thenReturn(Optional.of(vendorB));
        when(eligibilityService.evaluate(any(), any(), any())).thenReturn(
                new EligibilityService.EligibilityResult(true, List.of(), OffsetDateTime.now()));
        when(eligibilityService.duraSource(any())).thenReturn(
                Optional.of(new EligibilityService.DuraSource(UUID.randomUUID(), UUID.randomUUID())));
        when(eligibilityService.hasControlledAuthority(any())).thenReturn(true);

        when(inventoryClient.reserve(any(), any(), anyString(), anyInt(), any(), anyString(),
                anyString(), any(), any(), any()))
                .thenAnswer(inv -> Optional.of(new InventoryClient.DuraReservation(
                        UUID.randomUUID(), "ACTIVE", OffsetDateTime.now().plusHours(12))));
        when(inventoryClient.release(any(), any())).thenReturn(true);

        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(new OfferFinancialsService.FinancialBlock(
                        OfferFinancialsService.STATUS_SELF_PAY, FundingMode.SELF_PAY, "ZWG",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), null,
                        false, OfferFinancialsService.PA_NOT_REQUIRED, null, null, OffsetDateTime.now()));
        when(offerFinancialsService.toJson(any())).thenReturn(
                "{\"status\":\"SELF_PAY\",\"estimateNeverFinal\":true}");

        Map<String, Integer> requested = new LinkedHashMap<>();
        requested.put("L1", 2);
        requested.put("L2", 1);
        when(marketplaceRequestService.requestLineQuantities(any())).thenReturn(requested);
    }

    private FulfillmentOfferEntity offer(String offerId, String vendorId, String price) {
        FulfillmentOfferEntity offer = new FulfillmentOfferEntity();
        offer.setOfferId(offerId);
        offer.setTenantId(TENANT);
        offer.setRequestId(REQUEST_ID);
        offer.setInvitationId("01INV" + offerId.substring(5));
        offer.setVendorId(vendorId);
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setPriceTotal(new BigDecimal(price));
        offer.setCurrency("ZWG");
        offer.setFulfillmentWindowHours(12);
        offer.setTtlExpiresAt(OffsetDateTime.now().plusMinutes(30));
        when(offerRepository.findByOfferIdAndTenantId(offerId, TENANT)).thenReturn(Optional.of(offer));
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));
        return offer;
    }

    private FulfillmentOfferLineEntity line(String offerId, String lineRef, String itemCode, int qty) {
        FulfillmentOfferLineEntity line = new FulfillmentOfferLineEntity();
        line.setOfferLineId("01LIN" + offerId.substring(5) + lineRef);
        line.setTenantId(TENANT);
        line.setOfferId(offerId);
        line.setRequestLineRef(lineRef);
        line.setItemCode(itemCode);
        line.setQuantity(qty);
        line.setStockGrade(StockGrade.VERIFIED);
        return line;
    }

    private void stubOfferLines(String offerId, FulfillmentOfferLineEntity... lines) {
        when(offerLineRepository.findByOfferId(offerId)).thenReturn(List.of(lines));
    }

    private VendorProfileEntity vendor(String vendorId) {
        VendorProfileEntity vendor = new VendorProfileEntity();
        vendor.setVendorId(vendorId);
        vendor.setTenantId(TENANT);
        vendor.setStatus(VendorStatus.ACTIVE);
        vendor.setCoverage("{}");
        return vendor;
    }

    // ── combination validation — before any member commits ───────────────

    @Test
    void split_singleMember_isRefused() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> coordinator.selectSplit(REQUEST_ID, List.of(OFFER_A), TENANT,
                        "patient-1", "SPLIT-1", null));
        assertTrue(ex.getMessage().contains(
                SplitSelectionCoordinator.CODE_SPLIT_REQUIRES_MULTIPLE_OFFERS));
    }

    @Test
    void split_duplicateMember_isRefused() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> coordinator.selectSplit(REQUEST_ID, List.of(OFFER_A, OFFER_A), TENANT,
                        "patient-1", "SPLIT-2", null));
        assertTrue(ex.getMessage().contains(SplitSelectionCoordinator.CODE_DUPLICATE_MEMBER));
    }

    @Test
    void split_overlappingCoverage_isRefused_beforeAnyCommit() {
        // A covers L1; C covers L1+L2 — L1 would have two fulfillers.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> coordinator.selectSplit(REQUEST_ID, List.of(OFFER_A, OFFER_C), TENANT,
                        "patient-1", "SPLIT-3", null));
        assertTrue(ex.getMessage().contains(
                SplitSelectionCoordinator.CODE_OVERLAPPING_LINE_COVERAGE));
        // refusal happened BEFORE any member commit — nothing reserved, nothing selected
        assertTrue(selectionStore.isEmpty());
        verify(inventoryClient, never()).reserve(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyString(), any(), any(), any());
    }

    @Test
    void split_memberNotActive_isRefused() {
        offerB.setStatus(OfferStatus.WITHDRAWN);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> coordinator.selectSplit(REQUEST_ID, List.of(OFFER_A, OFFER_B), TENANT,
                        "patient-1", "SPLIT-4", null));
        assertTrue(ex.getMessage().contains(SplitSelectionCoordinator.CODE_MEMBER_NOT_ACTIVE));
        assertTrue(selectionStore.isEmpty());
    }

    // ── the split happy path — journey #45 ───────────────────────────────

    @Test
    void split_bothMembersCommit_requestCommitted_membersShareGroupId() {
        SplitSelectionCoordinator.SplitResult result = coordinator.selectSplit(
                REQUEST_ID, List.of(OFFER_A, OFFER_B), TENANT, "patient-1", "SPLIT-5", null);

        assertEquals(SplitSelectionCoordinator.OUTCOME_COMMITTED, result.outcome());
        assertEquals(2, result.members().size());
        assertTrue(result.members().stream().allMatch(
                SplitSelectionCoordinator.MemberResult::committed));
        assertTrue(result.coverage().complete());
        assertTrue(result.coverage().uncoveredLineRefs().isEmpty());

        // both selections committed, sharing the split group id
        assertNotNull(result.splitGroupId());
        List<SelectionEntity> selections = selectionStore.values().stream().toList();
        assertEquals(2, selections.size());
        assertTrue(selections.stream().allMatch(s ->
                s.getStatus() == SelectionStatus.COMMITTED
                        && result.splitGroupId().equals(s.getSplitGroupId())));

        // both offers committed; the request rolled to COMMITTED on full coverage
        assertEquals(OfferStatus.COMMITTED, offerA.getStatus());
        assertEquals(OfferStatus.COMMITTED, offerB.getStatus());
        assertEquals(MarketplaceRequestStatus.COMMITTED, request.getStatus());

        // one honest per-line reservation per member
        verify(inventoryClient).reserve(any(), any(), eq("ZIBO-A"), eq(2), any(),
                eq("MARKETPLACE_SELECTION"), anyString(), any(), any(), any());
        verify(inventoryClient).reserve(any(), any(), eq("ZIBO-B"), eq(1), any(),
                eq("MARKETPLACE_SELECTION"), anyString(), any(), any(), any());

        // events: member 1 left the request honestly partial; selection events
        // carry the split group; the rollup event closes the group
        assertTrue(outboxEvents.stream().anyMatch(e ->
                "REQUEST_PARTIALLY_COMMITTED".equals(e.getEventType())
                        && e.getPayload().contains(result.splitGroupId())));
        assertTrue(outboxEvents.stream().anyMatch(e ->
                "SELECTION_COMMITTED".equals(e.getEventType())
                        && e.getPayload().contains(result.splitGroupId())));
        assertTrue(outboxEvents.stream().anyMatch(e ->
                "SPLIT_SELECTION_OUTCOME".equals(e.getEventType())
                        && e.getPayload().contains("\"outcome\":\"COMMITTED\"")));
    }

    // ── per-member compensation isolation (§8.6.4 — committed members STAND) ──

    @Test
    void split_memberFails_partialCommit_committedSiblingStands() {
        // member B's stock is taken concurrently — its conditional reserve fails
        when(inventoryClient.reserve(any(), any(), eq("ZIBO-B"), anyInt(), any(), anyString(),
                anyString(), any(), any(), any())).thenReturn(Optional.empty());

        SplitSelectionCoordinator.SplitResult result = coordinator.selectSplit(
                REQUEST_ID, List.of(OFFER_A, OFFER_B), TENANT, "patient-1", "SPLIT-6", null);

        assertEquals(SplitSelectionCoordinator.OUTCOME_PARTIAL_COMMIT, result.outcome());
        SplitSelectionCoordinator.MemberResult memberA = result.members().get(0);
        SplitSelectionCoordinator.MemberResult memberB = result.members().get(1);
        assertTrue(memberA.committed());
        assertFalse(memberB.committed());
        assertEquals(CommitmentService.CODE_STOCK_UNAVAILABLE, memberB.outcomeCode());

        // member A STANDS — its offer committed, its hold never released
        assertEquals(OfferStatus.COMMITTED, offerA.getStatus());
        verify(inventoryClient, never()).release(any(), any());
        // member B honestly failed — re-offerable posture
        assertEquals(OfferStatus.FAILED_REVALIDATION, offerB.getStatus());
        assertEquals(CommitmentService.CODE_STOCK_UNAVAILABLE, offerB.getStatusReason());
        // the request stays live for the uncovered line — never a fake COMMITTED
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
        // honest rollup event
        assertTrue(outboxEvents.stream().anyMatch(e ->
                "SPLIT_SELECTION_OUTCOME".equals(e.getEventType())
                        && e.getPayload().contains("\"outcome\":\"PARTIAL_COMMIT\"")));
    }

    // ── replay idempotency (RC-1 across the whole combination) ───────────

    @Test
    void split_replaySameKey_returnsFirstOutcome_withoutReexecution() {
        coordinator.selectSplit(REQUEST_ID, List.of(OFFER_A, OFFER_B), TENANT,
                "patient-1", "SPLIT-7", null);
        assertEquals(MarketplaceRequestStatus.COMMITTED, request.getStatus());

        SplitSelectionCoordinator.SplitResult replay = coordinator.selectSplit(
                REQUEST_ID, List.of(OFFER_A, OFFER_B), TENANT, "patient-1", "SPLIT-7", null);

        assertEquals(SplitSelectionCoordinator.OUTCOME_COMMITTED, replay.outcome());
        assertTrue(replay.members().stream().allMatch(
                SplitSelectionCoordinator.MemberResult::replayed));
        // no re-execution: exactly one reservation per line across both calls
        verify(inventoryClient, times(2)).reserve(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyString(), any(), any(), any());
        assertEquals(2, selectionStore.size());
    }

    // ── honest partial coverage of the combination ───────────────────────

    @Test
    void split_combinationNotCoveringAllLines_commitsButRequestStaysLive() {
        request.setPublishedLinesJson("{\"lines\":["
                + "{\"lineRef\":\"L1\",\"itemCode\":\"ZIBO-A\",\"quantity\":2},"
                + "{\"lineRef\":\"L2\",\"itemCode\":\"ZIBO-B\",\"quantity\":1},"
                + "{\"lineRef\":\"L3\",\"itemCode\":\"ZIBO-C\",\"quantity\":1}]}");
        Map<String, Integer> requested = new LinkedHashMap<>();
        requested.put("L1", 2);
        requested.put("L2", 1);
        requested.put("L3", 1);
        when(marketplaceRequestService.requestLineQuantities(any())).thenReturn(requested);

        SplitSelectionCoordinator.SplitResult result = coordinator.selectSplit(
                REQUEST_ID, List.of(OFFER_A, OFFER_B), TENANT, "patient-1", "SPLIT-8", null);

        // every member committed — but the combination honestly does not cover L3
        assertEquals(SplitSelectionCoordinator.OUTCOME_COMMITTED, result.outcome());
        assertFalse(result.coverage().complete());
        assertEquals(List.of("L3"), result.coverage().uncoveredLineRefs());
        // the request never fakes completion — L3 stays offerable
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
    }

    @Test
    void split_underSuppliedQuantity_isSurfacedHonestly() {
        Map<String, Integer> requested = new LinkedHashMap<>();
        requested.put("L1", 2);
        requested.put("L2", 3); // B offers only 1 of 3
        when(marketplaceRequestService.requestLineQuantities(any())).thenReturn(requested);

        SplitSelectionCoordinator.SplitResult result = coordinator.selectSplit(
                REQUEST_ID, List.of(OFFER_A, OFFER_B), TENANT, "patient-1", "SPLIT-9", null);

        assertEquals(List.of("L2"), result.coverage().underSuppliedLineRefs());
    }

    // ── RC-1 per-line-coverage extension on single selects ───────────────

    @Test
    void rc1PerLine_overlappingSingleSelect_blocked_disjointAllowed() {
        when(offerRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of(offerA, offerB, offerC));

        // commit A alone (covers L1) — request stays live (L2 uncovered)
        CommitmentService.CommitResult first =
                commitmentService.commit(REQUEST_ID, OFFER_A, TENANT, "patient-1", "K1", null);
        assertTrue(first.committed());
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
        // C overlapped L1 → released; B disjoint → still ACTIVE (split posture)
        assertEquals(OfferStatus.NOT_SELECTED, offerC.getStatus());
        assertEquals(OfferStatus.ACTIVE, offerB.getStatus());

        // a full offer covering L1 again is refused per-line (re-stub C ACTIVE to isolate the guard)
        offerC.setStatus(OfferStatus.ACTIVE);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> commitmentService.commit(REQUEST_ID, OFFER_C, TENANT, "patient-1", "K2", null));
        assertTrue(ex.getMessage().contains("SELECTION_EXISTS"));

        // the disjoint member commits fine and closes the request
        CommitmentService.CommitResult second =
                commitmentService.commit(REQUEST_ID, OFFER_B, TENANT, "patient-1", "K3", null);
        assertTrue(second.committed());
        assertEquals(MarketplaceRequestStatus.COMMITTED, request.getStatus());
    }
}
