package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.integration.InventoryClient;
import zw.gov.mohcc.impilo.msikaflow.integration.OrosClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OF-B4/OF-B5 — request lifecycle: controlled gating at publication (§13.4),
 * eligibility-gated invitations with recorded refusals (§4.3 — never silent),
 * offer submission with §8E stock grading, and the §9.3/§9.4 sweeps.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceRequestServiceTest {

    @Mock private MarketplaceRequestRepository requestRepository;
    @Mock private MarketplaceInvitationRepository invitationRepository;
    @Mock private FulfillmentOfferRepository offerRepository;
    @Mock private FulfillmentOfferLineRepository offerLineRepository;
    @Mock private VendorProfileRepository vendorRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private EligibilityService eligibilityService;
    @Mock private OfferFinancialsService offerFinancialsService;
    @Mock private OrosClient orosClient;
    @Mock private InventoryClient inventoryClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private MarketplaceRequestService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String REQUEST_ID = "01REQAAAAAAAAAAAAAAAAAAAAA";
    private static final String VENDOR_A = "01VENAAAAAAAAAAAAAAAAAAAAA";
    private static final String VENDOR_B = "01VENBBBBBBBBBBBBBBBBBBBBB";

    @BeforeEach
    void setUp() {
        service = new MarketplaceRequestService(requestRepository, invitationRepository,
                offerRepository, offerLineRepository, vendorRepository, outboxRepository,
                eligibilityService, offerFinancialsService, orosClient, inventoryClient, mapper,
                120, 120, 60, true);
        // OF-B9 default: financial block computed per offer; UNVERIFIED never hides an offer.
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(new OfferFinancialsService.FinancialBlock(
                        OfferFinancialsService.STATUS_SELF_PAY, FundingMode.SELF_PAY, "ZWG",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), null,
                        false, OfferFinancialsService.PA_NOT_REQUIRED, null, null, OffsetDateTime.now()));

        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orosClient.orderVersionExists(anyString(), anyString(), any()))
                .thenReturn(Optional.of(Boolean.TRUE));
        when(eligibilityService.toSnapshotJson(any())).thenReturn("{\"eligible\":true}");
    }

    private MarketplaceRequestService.CreateCommand createCommand(
            PublicationMode mode, boolean controlled, List<String> invited) {
        return new MarketplaceRequestService.CreateCommand(
                "OROS-1", "OV-1", MarketplaceProfile.MEDICATION, mode, "zone-1", "ROUTINE",
                List.of(new PublishedSnapshotBuilder.RequestLine(
                        "L1", "ZIBO-X", "ZIBO", 2, "TAB", controlled, false, true)),
                invited, null, null, null);
    }

    private VendorProfileEntity vendor(String id) {
        VendorProfileEntity vendor = new VendorProfileEntity();
        vendor.setVendorId(id);
        vendor.setTenantId(TENANT);
        vendor.setStatus(VendorStatus.ACTIVE);
        vendor.setCoverage("{}");
        return vendor;
    }

    private MarketplaceRequestEntity storedRequest(MarketplaceRequestStatus status, boolean controlled) {
        MarketplaceRequestEntity request = new MarketplaceRequestEntity();
        request.setRequestId(REQUEST_ID);
        request.setTenantId(TENANT);
        request.setOrosOrderId("OROS-1");
        request.setOrosOrderVersionId("OV-1");
        request.setProfile(MarketplaceProfile.MEDICATION);
        request.setPublicationMode(PublicationMode.INVITED);
        request.setStatus(status);
        request.setControlled(controlled);
        request.setCoarseZone("zone-1");
        request.setInvitedVendorIds("[\"" + VENDOR_A + "\",\"" + VENDOR_B + "\"]");
        request.setPublishedLinesJson(
                "{\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"ZIBO-X\",\"quantity\":2,"
                        + "\"controlled\":" + controlled + ",\"coldChain\":false}]}");
        when(requestRepository.findByRequestIdAndTenantId(REQUEST_ID, TENANT))
                .thenReturn(Optional.of(request));
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        return request;
    }

    private EligibilityService.EligibilityResult eligible() {
        return new EligibilityService.EligibilityResult(true, List.of(), OffsetDateTime.now());
    }

    private EligibilityService.EligibilityResult ineligible(String code) {
        return new EligibilityService.EligibilityResult(false,
                List.of(new EligibilityService.PreconditionResult(1, "P", "FAIL", code, null)),
                OffsetDateTime.now());
    }

    // ── creation gating ──────────────────────────────────────────────────

    @Test
    void controlledRequest_openBroadcast_isForbidden() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createFromOrder(TENANT, "actor",
                        createCommand(PublicationMode.OPEN, true, null), null));
        assertTrue(ex.getMessage().contains("CONTROLLED_OPEN_BROADCAST_FORBIDDEN"));
        verifyNoInteractions(orosClient);
    }

    @Test
    void unsupportedPublicationMode_isRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createFromOrder(TENANT, "actor",
                        createCommand(PublicationMode.PAYER_NETWORK, false, null), null));
        assertTrue(ex.getMessage().contains("PUBLICATION_MODE_UNSUPPORTED"));
    }

    @Test
    void versionPin_unknownVersion_isRejected() {
        when(orosClient.orderVersionExists("OROS-1", "OV-1", null))
                .thenReturn(Optional.of(Boolean.FALSE));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createFromOrder(TENANT, "actor",
                        createCommand(PublicationMode.INVITED, false, List.of(VENDOR_A)), null));
        assertTrue(ex.getMessage().contains("ORDER_VERSION_UNKNOWN"));
    }

    @Test
    void versionPin_orosUnreachable_failsClosed() {
        when(orosClient.orderVersionExists("OROS-1", "OV-1", null)).thenReturn(Optional.empty());
        assertThrows(UpstreamUnavailableException.class,
                () -> service.createFromOrder(TENANT, "actor",
                        createCommand(PublicationMode.INVITED, false, List.of(VENDOR_A)), null));
    }

    @Test
    void create_producesDraft_withMinimisedSnapshot() {
        MarketplaceRequestEntity request = service.createFromOrder(TENANT, "actor",
                createCommand(PublicationMode.INVITED, true, List.of(VENDOR_A)), null);
        assertEquals(MarketplaceRequestStatus.DRAFT, request.getStatus());
        assertTrue(request.isControlled());
        assertEquals("OV-1", request.getOrosOrderVersionId());
        assertNotNull(request.getPublishedLinesJson());
        assertTrue(request.getPublishedLinesJson().contains("ZIBO-X"));
    }

    @Test
    void patientRequestedCheck_requiresExactlyOneVendor() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createFromOrder(TENANT, "actor",
                        createCommand(PublicationMode.PATIENT_REQUESTED_CHECK, false,
                                List.of(VENDOR_A, VENDOR_B)), null));
    }

    // ── publication + eligibility gating ─────────────────────────────────

    @Test
    void publish_invitesEligible_recordsRefusals_neverSilentlyDrops() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.DRAFT, false);
        VendorProfileEntity vendorA = vendor(VENDOR_A);
        VendorProfileEntity vendorB = vendor(VENDOR_B);
        when(vendorRepository.findById(VENDOR_A)).thenReturn(Optional.of(vendorA));
        when(vendorRepository.findById(VENDOR_B)).thenReturn(Optional.of(vendorB));
        when(eligibilityService.evaluate(eq(vendorA), any(), any())).thenReturn(eligible());
        when(eligibilityService.evaluate(eq(vendorB), any(), any()))
                .thenReturn(ineligible(EligibilityService.CODE_LICENCE_NOT_ACTIVE));

        MarketplaceRequestService.PublishOutcome outcome =
                service.publish(REQUEST_ID, TENANT, "actor", null);

        assertEquals(1, outcome.invitations().size());
        assertEquals(VENDOR_A, outcome.invitations().get(0).getVendorId());
        assertEquals(InvitationStatus.SENT, outcome.invitations().get(0).getStatus());
        assertNotNull(outcome.invitations().get(0).getEligibilitySnapshotJson());
        assertEquals(EligibilityService.CODE_LICENCE_NOT_ACTIVE, outcome.refusals().get(VENDOR_B));
        assertEquals(MarketplaceRequestStatus.COLLECTING_OFFERS, request.getStatus());
        assertNotNull(request.getOfferWindowEndsAt());
        assertNotNull(request.getSelectionWindowEndsAt());

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals("REQUEST_PUBLISHED", outbox.getValue().getEventType());
    }

    @Test
    void publish_controlled_excludesVendorsWithoutControlledAuthority_beforeExposure() {
        storedRequest(MarketplaceRequestStatus.DRAFT, true);
        VendorProfileEntity vendorA = vendor(VENDOR_A);
        VendorProfileEntity vendorB = vendor(VENDOR_B);
        when(vendorRepository.findById(VENDOR_A)).thenReturn(Optional.of(vendorA));
        when(vendorRepository.findById(VENDOR_B)).thenReturn(Optional.of(vendorB));
        when(eligibilityService.hasControlledAuthority(vendorA)).thenReturn(true);
        when(eligibilityService.hasControlledAuthority(vendorB)).thenReturn(false);
        when(eligibilityService.evaluate(eq(vendorA), any(), any())).thenReturn(eligible());

        MarketplaceRequestService.PublishOutcome outcome =
                service.publish(REQUEST_ID, TENANT, "actor", null);

        assertEquals(1, outcome.invitations().size());
        assertEquals(VENDOR_A, outcome.invitations().get(0).getVendorId());
        assertEquals(EligibilityService.CODE_CONTROLLED_AUTHORITY_MISSING,
                outcome.refusals().get(VENDOR_B));
        // §13.4: the ineligible vendor was never even evaluated against the controlled content
        verify(eligibilityService, never()).evaluate(eq(vendorB), any(), any());
    }

    @Test
    void publish_noEligibleVendors_throws_requestStaysDraft() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.DRAFT, false);
        when(vendorRepository.findById(anyString())).thenReturn(Optional.of(vendor(VENDOR_A)));
        when(eligibilityService.evaluate(any(), any(), any()))
                .thenReturn(ineligible(EligibilityService.CODE_VENDOR_NOT_IN_GOOD_STANDING));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.publish(REQUEST_ID, TENANT, "actor", null));
        assertTrue(ex.getMessage().contains("NO_ELIGIBLE_VENDORS"));
        assertEquals(MarketplaceRequestStatus.DRAFT, request.getStatus());
    }

    // ── offer submission (OF-B6) ─────────────────────────────────────────

    private MarketplaceInvitationEntity invitation(String vendorId) {
        MarketplaceInvitationEntity invitation = new MarketplaceInvitationEntity();
        invitation.setInvitationId("01INVAAAAAAAAAAAAAAAAAAAAA");
        invitation.setTenantId(TENANT);
        invitation.setRequestId(REQUEST_ID);
        invitation.setVendorId(vendorId);
        invitation.setStatus(InvitationStatus.SENT);
        return invitation;
    }

    @Test
    void submitOffer_activatesOffer_gradesStock_marksInvitationOffered() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.COLLECTING_OFFERS, false);
        request.setOfferWindowEndsAt(OffsetDateTime.now().plusMinutes(30));
        MarketplaceInvitationEntity invitation = invitation(VENDOR_A);
        when(invitationRepository.findFirstByRequestIdAndVendorId(REQUEST_ID, VENDOR_A))
                .thenReturn(Optional.of(invitation));
        VendorProfileEntity vendorA = vendor(VENDOR_A);
        when(vendorRepository.findById(VENDOR_A)).thenReturn(Optional.of(vendorA));
        when(eligibilityService.evaluate(eq(vendorA), any(), any())).thenReturn(eligible());
        UUID facility = UUID.randomUUID();
        UUID store = UUID.randomUUID();
        when(eligibilityService.duraSource(vendorA))
                .thenReturn(Optional.of(new EligibilityService.DuraSource(facility, store)));
        when(inventoryClient.availability(eq(facility), eq(store), eq("ZIBO-X"), any()))
                .thenReturn(Optional.of(new InventoryClient.DuraAvailability(10, 2, 12)));

        FulfillmentOfferEntity offer = service.submitOffer(REQUEST_ID, TENANT,
                new MarketplaceRequestService.SubmitOfferCommand(VENDOR_A, new BigDecimal("99.50"),
                        "ZWG", 12, 45L,
                        List.of(new MarketplaceRequestService.OfferLineCommand(
                                "L1", "ZIBO-X", 2, new BigDecimal("49.75"), false))),
                null);

        assertEquals(OfferStatus.ACTIVE, offer.getStatus());
        assertNotNull(offer.getTtlExpiresAt());
        assertEquals(InvitationStatus.OFFERED, invitation.getStatus());
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());

        ArgumentCaptor<FulfillmentOfferLineEntity> lineCaptor =
                ArgumentCaptor.forClass(FulfillmentOfferLineEntity.class);
        verify(offerLineRepository).save(lineCaptor.capture());
        assertEquals(StockGrade.VERIFIED, lineCaptor.getValue().getStockGrade());
    }

    @Test
    void submitOffer_duraUnreachable_gradeIsReported_neverSilentlyVerified() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.COLLECTING_OFFERS, false);
        request.setOfferWindowEndsAt(OffsetDateTime.now().plusMinutes(30));
        when(invitationRepository.findFirstByRequestIdAndVendorId(REQUEST_ID, VENDOR_A))
                .thenReturn(Optional.of(invitation(VENDOR_A)));
        VendorProfileEntity vendorA = vendor(VENDOR_A);
        when(vendorRepository.findById(VENDOR_A)).thenReturn(Optional.of(vendorA));
        when(eligibilityService.evaluate(eq(vendorA), any(), any())).thenReturn(eligible());
        when(eligibilityService.duraSource(vendorA)).thenReturn(Optional.empty());

        service.submitOffer(REQUEST_ID, TENANT,
                new MarketplaceRequestService.SubmitOfferCommand(VENDOR_A, BigDecimal.TEN,
                        "ZWG", null, null,
                        List.of(new MarketplaceRequestService.OfferLineCommand(
                                "L1", "ZIBO-X", 2, null, false))),
                null);

        ArgumentCaptor<FulfillmentOfferLineEntity> lineCaptor =
                ArgumentCaptor.forClass(FulfillmentOfferLineEntity.class);
        verify(offerLineRepository).save(lineCaptor.capture());
        assertEquals(StockGrade.REPORTED, lineCaptor.getValue().getStockGrade());
    }

    @Test
    void submitOffer_uninvitedVendor_isRejected() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.COLLECTING_OFFERS, false);
        request.setOfferWindowEndsAt(OffsetDateTime.now().plusMinutes(30));
        when(invitationRepository.findFirstByRequestIdAndVendorId(REQUEST_ID, VENDOR_B))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitOffer(REQUEST_ID, TENANT,
                        new MarketplaceRequestService.SubmitOfferCommand(VENDOR_B, BigDecimal.ONE,
                                "ZWG", null, null,
                                List.of(new MarketplaceRequestService.OfferLineCommand(
                                        "L1", "ZIBO-X", 1, null, false))),
                        null));
        assertTrue(ex.getMessage().contains("VENDOR_NOT_INVITED"));
    }

    @Test
    void submitOffer_eligibilityLapsedSinceInvitation_isRefusedWithCode() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.COLLECTING_OFFERS, false);
        request.setOfferWindowEndsAt(OffsetDateTime.now().plusMinutes(30));
        when(invitationRepository.findFirstByRequestIdAndVendorId(REQUEST_ID, VENDOR_A))
                .thenReturn(Optional.of(invitation(VENDOR_A)));
        VendorProfileEntity vendorA = vendor(VENDOR_A);
        when(vendorRepository.findById(VENDOR_A)).thenReturn(Optional.of(vendorA));
        when(eligibilityService.evaluate(eq(vendorA), any(), any()))
                .thenReturn(ineligible(EligibilityService.CODE_LICENCE_NOT_ACTIVE));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.submitOffer(REQUEST_ID, TENANT,
                        new MarketplaceRequestService.SubmitOfferCommand(VENDOR_A, BigDecimal.ONE,
                                "ZWG", null, null,
                                List.of(new MarketplaceRequestService.OfferLineCommand(
                                        "L1", "ZIBO-X", 1, null, false))),
                        null));
        assertTrue(ex.getMessage().contains(EligibilityService.CODE_LICENCE_NOT_ACTIVE));
    }

    @Test
    void submitOffer_afterWindowClosed_isRejected() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.COLLECTING_OFFERS, false);
        request.setOfferWindowEndsAt(OffsetDateTime.now().minusMinutes(1));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.submitOffer(REQUEST_ID, TENANT,
                        new MarketplaceRequestService.SubmitOfferCommand(VENDOR_A, BigDecimal.ONE,
                                "ZWG", null, null,
                                List.of(new MarketplaceRequestService.OfferLineCommand(
                                        "L1", "ZIBO-X", 1, null, false))),
                        null));
        assertTrue(ex.getMessage().contains("OFFER_WINDOW_CLOSED"));
    }

    // ── cancel ───────────────────────────────────────────────────────────

    @Test
    void cancel_releasesLiveOffers_withCodedReason() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.OFFERS_AVAILABLE, false);
        FulfillmentOfferEntity active = new FulfillmentOfferEntity();
        active.setOfferId("01OFFAAAAAAAAAAAAAAAAAAAAA");
        active.setRequestId(REQUEST_ID);
        active.setVendorId(VENDOR_A);
        active.setStatus(OfferStatus.ACTIVE);
        active.setPriceTotal(BigDecimal.ONE);
        active.setCurrency("ZWG");
        when(offerRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of(active));

        MarketplaceRequestEntity cancelled = service.cancel(REQUEST_ID, TENANT, "patient changed mind");
        assertEquals(MarketplaceRequestStatus.CANCELLED, cancelled.getStatus());
        assertEquals(OfferStatus.NOT_SELECTED, active.getStatus());
        assertEquals("REQUEST_CANCELLED", active.getStatusReason());
    }

    // ── sweeps (§9.3/§9.4 timers) ────────────────────────────────────────

    @Test
    void sweepExpiredOffers_flipsLapsedActiveOffers() {
        storedRequest(MarketplaceRequestStatus.OFFERS_AVAILABLE, false);
        FulfillmentOfferEntity lapsed = new FulfillmentOfferEntity();
        lapsed.setOfferId("01OFFAAAAAAAAAAAAAAAAAAAAA");
        lapsed.setRequestId(REQUEST_ID);
        lapsed.setVendorId(VENDOR_A);
        lapsed.setStatus(OfferStatus.ACTIVE);
        lapsed.setPriceTotal(BigDecimal.ONE);
        lapsed.setCurrency("ZWG");
        lapsed.setTtlExpiresAt(OffsetDateTime.now().minusMinutes(5));
        when(offerRepository.findByStatusAndTtlExpiresAtBefore(eq(OfferStatus.ACTIVE), any()))
                .thenReturn(List.of(lapsed));

        int swept = service.sweepExpiredOffers(OffsetDateTime.now());
        assertEquals(1, swept);
        assertEquals(OfferStatus.EXPIRED, lapsed.getStatus());
    }

    @Test
    void sweepOfferWindows_zeroOffers_becomesFailedNoOffers() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.COLLECTING_OFFERS, false);
        when(requestRepository.findByStatusInAndOfferWindowEndsAtBefore(any(), any()))
                .thenReturn(List.of(request));
        when(offerRepository.findByRequestIdAndStatus(REQUEST_ID, OfferStatus.ACTIVE))
                .thenReturn(List.of());

        service.sweepOfferWindows(OffsetDateTime.now());
        assertEquals(MarketplaceRequestStatus.FAILED_NO_OFFERS, request.getStatus());
    }

    @Test
    void sweepOfferWindows_withActiveOffer_becomesOffersAvailable() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.COLLECTING_OFFERS, false);
        when(requestRepository.findByStatusInAndOfferWindowEndsAtBefore(any(), any()))
                .thenReturn(List.of(request));
        FulfillmentOfferEntity active = new FulfillmentOfferEntity();
        active.setStatus(OfferStatus.ACTIVE);
        when(offerRepository.findByRequestIdAndStatus(REQUEST_ID, OfferStatus.ACTIVE))
                .thenReturn(List.of(active));

        service.sweepOfferWindows(OffsetDateTime.now());
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
    }

    @Test
    void sweepSelectionWindows_lapsedRequest_expires_withItsOffers() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.OFFERS_AVAILABLE, false);
        when(requestRepository.findByStatusInAndSelectionWindowEndsAtBefore(any(), any()))
                .thenReturn(List.of(request));
        FulfillmentOfferEntity active = new FulfillmentOfferEntity();
        active.setOfferId("01OFFAAAAAAAAAAAAAAAAAAAAA");
        active.setRequestId(REQUEST_ID);
        active.setStatus(OfferStatus.ACTIVE);
        when(offerRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of(active));

        service.sweepSelectionWindows(OffsetDateTime.now());
        assertEquals(MarketplaceRequestStatus.EXPIRED, request.getStatus());
        assertEquals(OfferStatus.EXPIRED, active.getStatus());
    }

    // ── comparison ranking labels ────────────────────────────────────────

    @Test
    void listOffers_labelsCheapestAndComplete() {
        MarketplaceRequestEntity request = storedRequest(MarketplaceRequestStatus.OFFERS_AVAILABLE, false);

        FulfillmentOfferEntity cheapComplete = new FulfillmentOfferEntity();
        cheapComplete.setOfferId("01OFFAAAAAAAAAAAAAAAAAAAAA");
        cheapComplete.setRequestId(REQUEST_ID);
        cheapComplete.setVendorId(VENDOR_A);
        cheapComplete.setStatus(OfferStatus.ACTIVE);
        cheapComplete.setPriceTotal(new BigDecimal("50.00"));
        cheapComplete.setCurrency("ZWG");
        FulfillmentOfferEntity pricier = new FulfillmentOfferEntity();
        pricier.setOfferId("01OFFBBBBBBBBBBBBBBBBBBBBB");
        pricier.setRequestId(REQUEST_ID);
        pricier.setVendorId(VENDOR_B);
        pricier.setStatus(OfferStatus.ACTIVE);
        pricier.setPriceTotal(new BigDecimal("80.00"));
        pricier.setCurrency("ZWG");
        when(offerRepository.findByRequestIdAndStatus(REQUEST_ID, OfferStatus.ACTIVE))
                .thenReturn(List.of(cheapComplete, pricier));

        FulfillmentOfferLineEntity fullLine = new FulfillmentOfferLineEntity();
        fullLine.setOfferId(cheapComplete.getOfferId());
        fullLine.setRequestLineRef("L1");
        fullLine.setItemCode("ZIBO-X");
        fullLine.setQuantity(2);
        fullLine.setStockGrade(StockGrade.VERIFIED);
        when(offerLineRepository.findByOfferId(cheapComplete.getOfferId())).thenReturn(List.of(fullLine));
        when(offerLineRepository.findByOfferId(pricier.getOfferId())).thenReturn(List.of());
        when(vendorRepository.findById(anyString())).thenReturn(Optional.of(vendor(VENDOR_A)));
        when(eligibilityService.coversZone(any(), anyString())).thenReturn(false);

        List<MarketplaceRequestService.RankedOffer> ranked = service.listOffers(REQUEST_ID, TENANT, null);
        assertEquals(2, ranked.size());
        // complete offer ranks first (clinical completeness dominates price — §4.5)
        assertEquals(cheapComplete.getOfferId(), ranked.get(0).offer().getOfferId());
        assertTrue(ranked.get(0).rankedBecause().containsAll(List.of("SUITABLE", "CHEAPEST", "COMPLETE")));
        assertTrue(ranked.get(1).rankedBecause().contains("SUITABLE"));
        assertFalse(ranked.get(1).rankedBecause().contains("COMPLETE"));
        // OF-B9: every ranked offer carries its financial block
        assertNotNull(ranked.get(0).financials());
        assertNotNull(ranked.get(1).financials());
        verify(offerFinancialsService, times(2)).compute(any(), any(), any(), any());
    }

    // ── OF-B8/OF-B9: funding election validation (fail-closed ambiguity) ──

    @Test
    void create_payerCoveredWithoutCoverageId_isRejected() {
        MarketplaceRequestService.CreateCommand cmd = new MarketplaceRequestService.CreateCommand(
                "OROS-1", "OV-1", MarketplaceProfile.MEDICATION, PublicationMode.INVITED,
                "zone-1", "ROUTINE",
                List.of(new PublishedSnapshotBuilder.RequestLine(
                        "L1", "ZIBO-X", "ZIBO", 2, "TAB", false, false, true)),
                List.of(VENDOR_A), null, FundingMode.PAYER_COVERED, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createFromOrder(TENANT, "actor-1", cmd, null));
        assertTrue(ex.getMessage().contains("COVERAGE_REF_REQUIRED"));
    }

    @Test
    void create_payerCoveredWithCoverageId_persistsElection() {
        UUID coverageId = UUID.randomUUID();
        MarketplaceRequestService.CreateCommand cmd = new MarketplaceRequestService.CreateCommand(
                "OROS-1", "OV-1", MarketplaceProfile.MEDICATION, PublicationMode.INVITED,
                "zone-1", "ROUTINE",
                List.of(new PublishedSnapshotBuilder.RequestLine(
                        "L1", "ZIBO-X", "ZIBO", 2, "TAB", false, false, true)),
                List.of(VENDOR_A), null, FundingMode.PAYER_COVERED, coverageId);
        MarketplaceRequestEntity request = service.createFromOrder(TENANT, "actor-1", cmd, null);
        assertEquals(FundingMode.PAYER_COVERED, request.getFundingMode());
        assertEquals(coverageId, request.getCoverageId());
        // §11.8: the coverage ref never leaks into the published snapshot
        assertFalse(request.getPublishedLinesJson().contains(coverageId.toString()));
    }
}
