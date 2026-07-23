package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OF-B13 — diagnostics fulfilment over the RFO rails (journeys #56/#57):
 * PII-free lab/imaging snapshot (coded lines + modality + phlebotomy
 * home-collection capability flag, §11.8), home-collection offer variant with a
 * mandatory window (§8.5), commitment step-5 honesty (service-capacity promise,
 * no DURA hold), step-6 skip (no prescription), and the OROS-spine seam
 * (orosOrderId + collection expectation on the committed selection — actual
 * specimen/result flow stays on the LIVE OROS diagnostics spine).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiagnosticsFulfilmentTest {

    @Mock private MarketplaceRequestRepository requestRepository;
    @Mock private MarketplaceInvitationRepository invitationRepository;
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

    private final ObjectMapper mapper = new ObjectMapper();
    private MarketplaceRequestService requestService;
    private CommitmentService commitmentService;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String REQUEST_ID = "01REQDIAGAAAAAAAAAAAAAAAAA";
    private static final String OFFER_ID = "01OFFDIAGAAAAAAAAAAAAAAAAA";
    private static final String VENDOR_ID = "01VENLABAAAAAAAAAAAAAAAAAA";

    private MarketplaceRequestEntity request;
    private FulfillmentOfferEntity offer;
    private FulfillmentOfferLineEntity line;
    private VendorProfileEntity vendor;
    private final List<EventOutboxEntity> outboxEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        requestService = new MarketplaceRequestService(requestRepository, invitationRepository,
                offerRepository, offerLineRepository, vendorRepository, outboxRepository,
                eligibilityService, offerFinancialsService, orosClient, inventoryClient, mapper,
                120, 120, 60, true);
        commitmentService = new CommitmentService(requestRepository, offerRepository,
                offerLineRepository, selectionRepository, vendorRepository, reservationRepository,
                outboxRepository, eligibilityService, inventoryClient, orosClient,
                offerFinancialsService, mushexClient, fulfilmentDispatchService, mapper, 24);

        when(fulfilmentDispatchService.dispatchOnCommit(any(), any(), any(), any(), any()))
                .thenReturn(new FulfilmentDispatchService.DispatchOutcome(
                        FulfilmentDispatchService.DISPATCH_NOT_REQUIRED, null,
                        "no pathway election — PICKUP assumed (fail-closed)"));

        request = new MarketplaceRequestEntity();
        request.setRequestId(REQUEST_ID);
        request.setTenantId(TENANT);
        request.setOrosOrderId("OROS-LAB-1");
        request.setOrosOrderVersionId("OV-1");
        request.setProfile(MarketplaceProfile.DIAGNOSTICS);
        request.setPublicationMode(PublicationMode.INVITED);
        request.setStatus(MarketplaceRequestStatus.OFFERS_AVAILABLE);
        request.setHomeCollectionRequested(true);
        request.setPublishedLinesJson(
                "{\"profile\":\"DIAGNOSTICS\",\"capabilityFlags\":{\"phlebotomyHomeCollection\":true},"
                        + "\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"ZIBO-LAB-FBC\",\"quantity\":1}]}");

        offer = new FulfillmentOfferEntity();
        offer.setOfferId(OFFER_ID);
        offer.setTenantId(TENANT);
        offer.setRequestId(REQUEST_ID);
        offer.setInvitationId("01INVDIAGAAAAAAAAAAAAAAAAA");
        offer.setVendorId(VENDOR_ID);
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setPriceTotal(new BigDecimal("45.00"));
        offer.setCurrency("ZWG");
        offer.setFulfillmentWindowHours(24);
        offer.setTtlExpiresAt(OffsetDateTime.now().plusMinutes(30));
        offer.setHomeCollection(true);
        offer.setCollectionWindowStart(OffsetDateTime.now().plusHours(2));
        offer.setCollectionWindowEnd(OffsetDateTime.now().plusHours(4));

        line = new FulfillmentOfferLineEntity();
        line.setOfferLineId("01LINDIAGAAAAAAAAAAAAAAAAA");
        line.setTenantId(TENANT);
        line.setOfferId(OFFER_ID);
        line.setRequestLineRef("L1");
        line.setItemCode("ZIBO-LAB-FBC");
        line.setQuantity(1);
        line.setStockGrade(StockGrade.REPORTED);

        vendor = new VendorProfileEntity();
        vendor.setVendorId(VENDOR_ID);
        vendor.setTenantId(TENANT);
        vendor.setStatus(VendorStatus.ACTIVE);
        vendor.setCoverage("{}");

        when(requestRepository.findByRequestIdAndTenantId(REQUEST_ID, TENANT))
                .thenReturn(Optional.of(request));
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerRepository.findByOfferIdAndTenantId(OFFER_ID, TENANT)).thenReturn(Optional.of(offer));
        when(offerRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of(offer));
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
        when(offerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerLineRepository.findByOfferId(OFFER_ID)).thenReturn(List.of(line));
        when(offerLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(selectionRepository.findFirstByTenantIdAndIdempotencyKey(any(), anyString()))
                .thenReturn(Optional.empty());
        when(selectionRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of());
        when(selectionRepository.save(any(SelectionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(inv -> {
            outboxEvents.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(orosClient.orderVersionExists(anyString(), anyString(), any()))
                .thenReturn(Optional.of(Boolean.TRUE));
        when(eligibilityService.evaluate(any(), any(), any())).thenReturn(
                new EligibilityService.EligibilityResult(true, List.of(), OffsetDateTime.now()));
        when(eligibilityService.toSnapshotJson(any())).thenReturn("{\"eligible\":true}");
        // A lab vendor has NO DURA facility/store refs — diagnostics must not need one.
        when(eligibilityService.duraSource(vendor)).thenReturn(Optional.empty());
        when(eligibilityService.hasControlledAuthority(vendor)).thenReturn(true);
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(new OfferFinancialsService.FinancialBlock(
                        OfferFinancialsService.STATUS_SELF_PAY, FundingMode.SELF_PAY, "ZWG",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), null,
                        false, OfferFinancialsService.PA_NOT_REQUIRED, null, null, OffsetDateTime.now()));
        when(offerFinancialsService.toJson(any())).thenReturn(
                "{\"status\":\"SELF_PAY\",\"estimateNeverFinal\":true}");
    }

    // ── §11.8 snapshot: coded diagnostics lines + capability flags, PII-free ──

    @Test
    void snapshot_diagnostics_carriesModalityAndHomeCollectionFlag_stillPiiFree() throws Exception {
        String snapshot = PublishedSnapshotBuilder.build(mapper, "DIAGNOSTICS", "ndila-zone-hre-04",
                "ROUTINE",
                List.of(
                        new PublishedSnapshotBuilder.RequestLine(
                                "L1", "ZIBO-LAB-FBC", "ZIBO", 1, "TEST", false, false, false, null),
                        new PublishedSnapshotBuilder.RequestLine(
                                "L2", "ZIBO-IMG-CXR", "ZIBO", 1, "STUDY", false, false, false, "XR")),
                true);

        JsonNode parsed = mapper.readTree(snapshot);
        assertEquals("DIAGNOSTICS", parsed.path("profile").asText());
        assertTrue(parsed.path("capabilityFlags").path("phlebotomyHomeCollection").asBoolean());
        assertEquals("XR", parsed.path("lines").get(1).path("modality").asText());
        assertFalse(parsed.path("lines").get(0).has("modality"), "no fabricated modality on lab lines");
    }

    @Test
    void snapshotFromRichDiagnosticsOrder_extractsModality_dropsPii() throws Exception {
        ObjectNode order = mapper.createObjectNode();
        order.put("patientName", "Tendai Moyo");
        order.put("diagnosis", "suspected pneumonia");
        order.put("address", "14 Samora Machel Ave");
        ArrayNode lines = order.putArray("lines");
        ObjectNode imaging = lines.addObject();
        imaging.put("lineRef", "L1");
        imaging.put("itemCode", "ZIBO-IMG-CXR");
        imaging.put("quantity", 1);
        imaging.put("modality", "XR");
        imaging.put("indication", "cough 3 weeks");

        String snapshot = PublishedSnapshotBuilder.buildFromOrder(
                mapper, order, "DIAGNOSTICS", "zone-1", "ROUTINE");
        String lower = snapshot.toLowerCase(Locale.ROOT);
        assertFalse(lower.contains("tendai"), "patient name leaked");
        assertFalse(lower.contains("pneumonia"), "diagnosis leaked");
        assertFalse(lower.contains("samora"), "address leaked");
        assertFalse(lower.contains("cough"), "indication leaked");
        JsonNode parsed = mapper.readTree(snapshot);
        assertEquals("XR", parsed.path("lines").get(0).path("modality").asText());
    }

    // ── request/offer validation ─────────────────────────────────────────

    @Test
    void create_homeCollectionOnMedicationProfile_isRefused() {
        MarketplaceRequestService.CreateCommand cmd = new MarketplaceRequestService.CreateCommand(
                "OROS-1", "OV-1", MarketplaceProfile.MEDICATION, PublicationMode.INVITED,
                "zone-1", "ROUTINE",
                List.of(new PublishedSnapshotBuilder.RequestLine(
                        "L1", "ZIBO-X", "ZIBO", 1, "TAB", false, false, true)),
                List.of(VENDOR_ID), null, null, null, Boolean.TRUE);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> requestService.createFromOrder(TENANT, "actor", cmd, null));
        assertTrue(ex.getMessage().contains("HOME_COLLECTION_PROFILE_MISMATCH"));
    }

    @Test
    void create_diagnosticsWithHomeCollection_persistsFlag_andSnapshotCapability() {
        MarketplaceRequestService.CreateCommand cmd = new MarketplaceRequestService.CreateCommand(
                "OROS-LAB-1", "OV-1", MarketplaceProfile.DIAGNOSTICS, PublicationMode.INVITED,
                "zone-1", "ROUTINE",
                List.of(new PublishedSnapshotBuilder.RequestLine(
                        "L1", "ZIBO-LAB-FBC", "ZIBO", 1, "TEST", false, false, false, null)),
                List.of(VENDOR_ID), null, null, null, Boolean.TRUE);
        MarketplaceRequestEntity created = requestService.createFromOrder(TENANT, "actor", cmd, null);
        assertTrue(created.isHomeCollectionRequested());
        assertTrue(created.getPublishedLinesJson().contains("phlebotomyHomeCollection"));
    }

    @Test
    void submitOffer_homeCollectionWithoutWindow_isRefused() {
        request.setStatus(MarketplaceRequestStatus.COLLECTING_OFFERS);
        request.setOfferWindowEndsAt(OffsetDateTime.now().plusMinutes(30));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> requestService.submitOffer(REQUEST_ID, TENANT,
                        new MarketplaceRequestService.SubmitOfferCommand(VENDOR_ID,
                                new BigDecimal("45.00"), "ZWG", 24, 60L,
                                List.of(new MarketplaceRequestService.OfferLineCommand(
                                        "L1", "ZIBO-LAB-FBC", 1, new BigDecimal("45.00"), false)),
                                Boolean.TRUE, null, null),
                        null));
        assertTrue(ex.getMessage().contains("COLLECTION_WINDOW_REQUIRED"));
    }

    @Test
    void submitOffer_homeCollectionOnMedicationRequest_isRefused() {
        request.setProfile(MarketplaceProfile.MEDICATION);
        request.setStatus(MarketplaceRequestStatus.COLLECTING_OFFERS);
        request.setOfferWindowEndsAt(OffsetDateTime.now().plusMinutes(30));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> requestService.submitOffer(REQUEST_ID, TENANT,
                        new MarketplaceRequestService.SubmitOfferCommand(VENDOR_ID,
                                new BigDecimal("45.00"), "ZWG", 24, 60L,
                                List.of(new MarketplaceRequestService.OfferLineCommand(
                                        "L1", "ZIBO-X", 1, new BigDecimal("45.00"), false)),
                                Boolean.TRUE, OffsetDateTime.now().plusHours(1),
                                OffsetDateTime.now().plusHours(2)),
                        null));
        assertTrue(ex.getMessage().contains("HOME_COLLECTION_PROFILE_MISMATCH"));
    }

    @Test
    void submitOffer_diagnostics_gradesReported_neverConsultsDuraLedger() {
        request.setStatus(MarketplaceRequestStatus.COLLECTING_OFFERS);
        request.setOfferWindowEndsAt(OffsetDateTime.now().plusMinutes(30));
        MarketplaceInvitationEntity invitation = new MarketplaceInvitationEntity();
        invitation.setInvitationId("01INVDIAGAAAAAAAAAAAAAAAAA");
        invitation.setTenantId(TENANT);
        invitation.setRequestId(REQUEST_ID);
        invitation.setVendorId(VENDOR_ID);
        invitation.setStatus(InvitationStatus.SENT);
        when(invitationRepository.findFirstByRequestIdAndVendorId(REQUEST_ID, VENDOR_ID))
                .thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Vendor WITH a DURA source: still no ledger lookup for a test code.
        when(eligibilityService.duraSource(vendor)).thenReturn(
                Optional.of(new EligibilityService.DuraSource(UUID.randomUUID(), UUID.randomUUID())));

        FulfillmentOfferEntity submitted = requestService.submitOffer(REQUEST_ID, TENANT,
                new MarketplaceRequestService.SubmitOfferCommand(VENDOR_ID,
                        new BigDecimal("45.00"), "ZWG", 24, 60L,
                        List.of(new MarketplaceRequestService.OfferLineCommand(
                                "L1", "ZIBO-LAB-FBC", 1, new BigDecimal("45.00"), false)),
                        Boolean.TRUE, OffsetDateTime.now().plusHours(2),
                        OffsetDateTime.now().plusHours(4)),
                null);

        assertTrue(submitted.isHomeCollection());
        assertNotNull(submitted.getCollectionWindowStart());
        verify(inventoryClient, never()).availability(any(), any(), anyString(), any());
        org.mockito.ArgumentCaptor<FulfillmentOfferLineEntity> captor =
                org.mockito.ArgumentCaptor.forClass(FulfillmentOfferLineEntity.class);
        verify(offerLineRepository).save(captor.capture());
        assertEquals(StockGrade.REPORTED, captor.getValue().getStockGrade());
    }

    // ── commitment: step-5 honesty + OROS-spine seam ─────────────────────

    @Test
    void diagnosticsCommit_skipsDuraReservation_honestly_andSkipsClaim() {
        CommitmentService.CommitResult result =
                commitmentService.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-DX1", null);

        assertTrue(result.committed());
        assertEquals(SelectionStatus.COMMITTED, result.selection().getStatus());
        // step 5: no DURA hold ever attempted — a service-capacity promise instead
        verify(inventoryClient, never()).reserve(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyString(), any(), any(), any());
        // step 6: no prescription claim for diagnostics
        verify(orosClient, never()).claimPrescription(anyString(), anyString(), anyString(), any());
        String stepLog = result.selection().getStepLogJson();
        assertTrue(stepLog.contains("DURA_RESERVATION"));
        assertTrue(stepLog.contains("SKIPPED"));
        assertTrue(stepLog.contains("service-capacity"));
        assertTrue(stepLog.contains("PRESCRIPTION_CLAIM"));
        assertTrue(stepLog.contains("DIAGNOSTICS profile"));
        // the lab vendor had NO DURA source and the commitment still stands
        assertEquals(MarketplaceRequestStatus.COMMITTED, request.getStatus());
    }

    @Test
    void diagnosticsCommit_recordsOrosSeam_andHomeCollectionExpectation() {
        CommitmentService.CommitResult result =
                commitmentService.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-DX2", null);

        SelectionEntity selection = result.selection();
        // OF-B13 seam: results route on the LIVE OROS spine via the order ref
        assertEquals("OROS-LAB-1", selection.getOrosOrderId());
        assertEquals("HOME_COLLECTION", selection.getCollectionMode());
        assertEquals(offer.getCollectionWindowStart(), selection.getCollectionWindowStart());
        assertEquals(offer.getCollectionWindowEnd(), selection.getCollectionWindowEnd());
        assertTrue(selection.getStepLogJson().contains("collectionMode=HOME_COLLECTION"));

        // the committed event carries the expectation — PII-free
        EventOutboxEntity committedEvent = outboxEvents.stream()
                .filter(e -> "SELECTION_COMMITTED".equals(e.getEventType()))
                .findFirst().orElseThrow();
        assertTrue(committedEvent.getPayload().contains("OROS-LAB-1"));
        assertTrue(committedEvent.getPayload().contains("HOME_COLLECTION"));
        assertTrue(committedEvent.getPayload().contains("DIAGNOSTICS"));
    }

    @Test
    void diagnosticsCommit_walkInOffer_recordsWalkInExpectation() {
        offer.setHomeCollection(false);
        offer.setCollectionWindowStart(null);
        offer.setCollectionWindowEnd(null);

        CommitmentService.CommitResult result =
                commitmentService.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-DX3", null);

        assertTrue(result.committed());
        assertEquals("WALK_IN", result.selection().getCollectionMode());
        assertNull(result.selection().getCollectionWindowStart());
    }
}
