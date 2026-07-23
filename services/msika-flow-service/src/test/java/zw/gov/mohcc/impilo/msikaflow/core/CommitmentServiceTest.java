package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * §8.9 commitment orchestrator — RC-1/2/3/6/7 discrete coded outcomes,
 * compensation, TTL re-binding, and the §13.4 controlled hard-gate (OF-B6/OF-B11).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitmentServiceTest {

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

    private final ObjectMapper mapper = new ObjectMapper();
    private CommitmentService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String REQUEST_ID = "01REQAAAAAAAAAAAAAAAAAAAAA";
    private static final String OFFER_ID = "01OFFAAAAAAAAAAAAAAAAAAAAA";
    private static final String OTHER_OFFER_ID = "01OFFBBBBBBBBBBBBBBBBBBBBB";
    private static final String VENDOR_ID = "01VENAAAAAAAAAAAAAAAAAAAAA";
    private static final UUID DURA_FACILITY = UUID.randomUUID();
    private static final UUID DURA_STORE = UUID.randomUUID();
    private static final UUID DURA_RESERVATION = UUID.randomUUID();

    private MarketplaceRequestEntity request;
    private FulfillmentOfferEntity offer;
    private FulfillmentOfferEntity otherOffer;
    private FulfillmentOfferLineEntity line;
    private VendorProfileEntity vendor;
    private final AtomicReference<SelectionEntity> savedSelection = new AtomicReference<>();
    private final AtomicReference<ReservationEntity> savedProjection = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new CommitmentService(requestRepository, offerRepository, offerLineRepository,
                selectionRepository, vendorRepository, reservationRepository, outboxRepository,
                eligibilityService, inventoryClient, orosClient,
                offerFinancialsService, mushexClient, fulfilmentDispatchService, mapper, 24);

        // OF-B17 default: pickup pathway (NULL election, fail-closed) — no
        // delivery dispatch; the logistics tests override per scenario.
        when(fulfilmentDispatchService.dispatchOnCommit(any(), any(), any(), any(), any()))
                .thenReturn(new FulfilmentDispatchService.DispatchOutcome(
                        FulfilmentDispatchService.DISPATCH_NOT_REQUIRED, null,
                        "no pathway election — PICKUP assumed (fail-closed)"));

        request = new MarketplaceRequestEntity();
        request.setRequestId(REQUEST_ID);
        request.setTenantId(TENANT);
        request.setOrosOrderId("OROS-1");
        request.setOrosOrderVersionId("OV-1");
        request.setProfile(MarketplaceProfile.MEDICATION);
        request.setPublicationMode(PublicationMode.INVITED);
        request.setStatus(MarketplaceRequestStatus.OFFERS_AVAILABLE);
        request.setPrescriptionToken("TOKEN-XYZ");
        request.setPublishedLinesJson("{\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"ZIBO-X\",\"quantity\":2}]}");

        offer = new FulfillmentOfferEntity();
        offer.setOfferId(OFFER_ID);
        offer.setTenantId(TENANT);
        offer.setRequestId(REQUEST_ID);
        offer.setInvitationId("01INVAAAAAAAAAAAAAAAAAAAAA");
        offer.setVendorId(VENDOR_ID);
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setPriceTotal(new BigDecimal("120.00"));
        offer.setCurrency("ZWG");
        offer.setFulfillmentWindowHours(12);
        offer.setTtlExpiresAt(OffsetDateTime.now().plusMinutes(30));

        otherOffer = new FulfillmentOfferEntity();
        otherOffer.setOfferId(OTHER_OFFER_ID);
        otherOffer.setTenantId(TENANT);
        otherOffer.setRequestId(REQUEST_ID);
        otherOffer.setInvitationId("01INVBBBBBBBBBBBBBBBBBBBBB");
        otherOffer.setVendorId("01VENBBBBBBBBBBBBBBBBBBBBB");
        otherOffer.setStatus(OfferStatus.ACTIVE);
        otherOffer.setPriceTotal(new BigDecimal("150.00"));
        otherOffer.setCurrency("ZWG");

        line = new FulfillmentOfferLineEntity();
        line.setOfferLineId("01LINAAAAAAAAAAAAAAAAAAAAA");
        line.setTenantId(TENANT);
        line.setOfferId(OFFER_ID);
        line.setRequestLineRef("L1");
        line.setItemCode("ZIBO-X");
        line.setQuantity(2);
        line.setStockGrade(StockGrade.VERIFIED);

        vendor = new VendorProfileEntity();
        vendor.setVendorId(VENDOR_ID);
        vendor.setTenantId(TENANT);
        vendor.setStatus(VendorStatus.ACTIVE);
        vendor.setCoverage("{}");

        when(selectionRepository.findFirstByTenantIdAndIdempotencyKey(any(), anyString()))
                .thenReturn(Optional.empty());
        when(selectionRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of());
        when(selectionRepository.save(any(SelectionEntity.class))).thenAnswer(inv -> {
            savedSelection.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(selectionRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(savedSelection.get()));
        when(requestRepository.findByRequestIdAndTenantId(REQUEST_ID, TENANT))
                .thenReturn(Optional.of(request));
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerRepository.findByOfferIdAndTenantId(OFFER_ID, TENANT)).thenReturn(Optional.of(offer));
        when(offerRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of(offer, otherOffer));
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
        when(offerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerLineRepository.findByOfferId(OFFER_ID)).thenReturn(List.of(line));
        when(offerLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(reservationRepository.save(any(ReservationEntity.class))).thenAnswer(inv -> {
            savedProjection.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(reservationRepository.findFirstByReservationRef(DURA_RESERVATION.toString()))
                .thenAnswer(inv -> Optional.ofNullable(savedProjection.get()));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(eligibilityService.evaluate(any(), any(), any())).thenReturn(eligible());
        when(eligibilityService.duraSource(vendor)).thenReturn(
                Optional.of(new EligibilityService.DuraSource(DURA_FACILITY, DURA_STORE)));
        when(eligibilityService.hasControlledAuthority(vendor)).thenReturn(true);
        when(eligibilityService.providerRef(vendor)).thenReturn("PRV-100");

        when(inventoryClient.reserve(any(), any(), anyString(), anyInt(), any(), anyString(),
                anyString(), any(), any(), any()))
                .thenReturn(Optional.of(new InventoryClient.DuraReservation(
                        DURA_RESERVATION, "ACTIVE", OffsetDateTime.now().plusHours(12))));
        when(inventoryClient.release(any(), any())).thenReturn(true);
        when(inventoryClient.recordControlledIssue(any(), any(), anyString(), any(), any(),
                any(), anyString(), any())).thenReturn(true);
        when(orosClient.claimPrescription(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(new OrosClient.PrescriptionClaim("CLAIM-1", "PV-1", false)));
        when(orosClient.releaseClaim(anyString(), anyString(), any())).thenReturn(true);

        // OF-B9/OF-B10 defaults: zero-shortfall self-pay block (step 8 WAIVED —
        // the pre-finance suite semantics); finance tests override per scenario.
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(BigDecimal.ZERO));
        when(offerFinancialsService.toJson(any())).thenAnswer(inv -> {
            OfferFinancialsService.FinancialBlock b = inv.getArgument(0);
            return "{\"status\":\"" + b.status() + "\",\"estimateNeverFinal\":true}";
        });
        when(mushexClient.createSelectionPaymentIntent(any(), anyString(), any(), any()))
                .thenReturn(Optional.of(new MushexClient.MushexIntentCreated("mpi-1", "CREATED")));
        when(mushexClient.tryRefund(any(), anyString(), any(), anyString()))
                .thenReturn(Optional.of(new MushexClient.MushexRefundCreated("ref-1", "PENDING")));
    }

    private static OfferFinancialsService.FinancialBlock selfPayBlock(BigDecimal amount) {
        return new OfferFinancialsService.FinancialBlock(
                OfferFinancialsService.STATUS_SELF_PAY, FundingMode.SELF_PAY, "ZWG",
                amount, BigDecimal.ZERO, amount, null, List.of(), null,
                false, OfferFinancialsService.PA_NOT_REQUIRED, null,
                "self-pay elected", OffsetDateTime.now());
    }

    private static OfferFinancialsService.FinancialBlock payerBlock(String status, BigDecimal covered,
                                                                    BigDecimal patient, boolean paRequired,
                                                                    String paStatus, UUID paRef) {
        return new OfferFinancialsService.FinancialBlock(
                status, FundingMode.PAYER_COVERED, "USD",
                covered != null && patient != null ? covered.add(patient) : null,
                covered, patient,
                UUID.randomUUID(), List.of(UUID.randomUUID()), "CPID-1",
                paRequired, paStatus, paRef, null, OffsetDateTime.now());
    }

    private EligibilityService.EligibilityResult eligible() {
        return new EligibilityService.EligibilityResult(true, List.of(), OffsetDateTime.now());
    }

    private EligibilityService.EligibilityResult ineligible(String code) {
        return new EligibilityService.EligibilityResult(false,
                List.of(new EligibilityService.PreconditionResult(1, "P", "FAIL", code, null)),
                OffsetDateTime.now());
    }

    // ── happy path ───────────────────────────────────────────────────────

    @Test
    void happyPath_stepsOrdered_reservesThenClaims_thenCommits() {
        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-1", null);

        assertTrue(result.committed());
        assertEquals(CommitmentService.CODE_COMMITTED, result.outcomeCode());
        assertEquals(SelectionStatus.COMMITTED, result.selection().getStatus());
        assertEquals("CLAIM-1", result.selection().getPrescriptionClaimId());
        assertNotNull(result.selection().getCommittedAt());
        assertEquals(OfferStatus.COMMITTED, offer.getStatus());
        assertEquals(MarketplaceRequestStatus.COMMITTED, request.getStatus());
        // losing offer released
        assertEquals(OfferStatus.NOT_SELECTED, otherOffer.getStatus());
        // step 5 before step 6 — reservation precedes the claim
        InOrder inOrder = inOrder(inventoryClient, orosClient);
        inOrder.verify(inventoryClient).reserve(eq(DURA_FACILITY), eq(DURA_STORE), eq("ZIBO-X"),
                eq(2), any(), eq("MARKETPLACE_SELECTION"), anyString(), any(), any(), any());
        inOrder.verify(orosClient).claimPrescription(eq("TOKEN-XYZ"), eq(VENDOR_ID), anyString(), any());
        // projection row written CONFIRMED with the DURA ref (demoted projection, OF-B11)
        assertNotNull(savedProjection.get());
        assertEquals(ReservationStatus.CONFIRMED, savedProjection.get().getStatus());
        assertEquals(DURA_RESERVATION.toString(), savedProjection.get().getReservationRef());
        assertEquals("INVENTORY", savedProjection.get().getSystem());
        // §9.4 binding: offer TTL re-bound to the reservation TTL
        assertNotNull(offer.getTtlExpiresAt());
        assertEquals(DURA_RESERVATION.toString(), line.getDuraReservationRef());
        // step log recorded
        assertNotNull(result.selection().getStepLogJson());
        assertTrue(result.selection().getStepLogJson().contains("DURA_RESERVATION"));
        // OF-B17: step 11 is REAL now — pickup pathway records the honest
        // SKIPPED entry (no delivery task; dispense rides the OROS claim).
        assertTrue(result.selection().getStepLogJson().contains("FULFILMENT_DISPATCH"));
        assertTrue(result.selection().getStepLogJson().contains("PICKUP"));
        // OF-B9/OF-B10: steps 7/8 are REAL — clearance passed, zero
        // shortfall recorded WAIVED.
        assertTrue(result.selection().getStepLogJson().contains("FINANCIAL_CLEARANCE"));
        assertTrue(result.selection().getStepLogJson().contains("PAYMENT_EXECUTION"));
        assertTrue(result.selection().getStepLogJson().contains("WAIVED"));
        // financial snapshot persisted with the binding estimate label
        assertNotNull(result.selection().getFinancialJson());
        assertTrue(result.selection().getFinancialJson().contains("estimateNeverFinal"));
        verify(inventoryClient, never()).release(any(), any());
    }

    // ── RC-2: offer TTL expired at revalidation ──────────────────────────

    @Test
    void rc2_ttlExpired_failsClosed_nothingReservedOrCharged() {
        offer.setTtlExpiresAt(OffsetDateTime.now().minusMinutes(1));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-2", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_OFFER_EXPIRED, result.outcomeCode());
        assertEquals(SelectionStatus.FAILED, result.selection().getStatus());
        assertEquals(OfferStatus.EXPIRED, offer.getStatus());
        // honest re-offer posture
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
        verify(inventoryClient, never()).reserve(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyString(), any(), any(), any());
        verify(orosClient, never()).claimPrescription(anyString(), anyString(), anyString(), any());
    }

    // ── RC-3: stock taken concurrently — conditional reserve fails ───────

    @Test
    void rc3_duraReserveFails_failedRevalidation_noClaim_gradeDropsToReported() {
        when(inventoryClient.reserve(any(), any(), anyString(), anyInt(), any(), anyString(),
                anyString(), any(), any(), any())).thenReturn(Optional.empty());

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-3", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_STOCK_UNAVAILABLE, result.outcomeCode());
        assertEquals(OfferStatus.FAILED_REVALIDATION, offer.getStatus());
        assertEquals(CommitmentService.CODE_STOCK_UNAVAILABLE, offer.getStatusReason());
        assertEquals(StockGrade.REPORTED, line.getStockGrade());
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
        verify(orosClient, never()).claimPrescription(anyString(), anyString(), anyString(), any());
    }

    @Test
    void rc3_vendorWithoutDuraSource_failsClosed_stockSourceUnverified() {
        when(eligibilityService.duraSource(vendor)).thenReturn(Optional.empty());

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-3b", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_STOCK_SOURCE_UNVERIFIED, result.outcomeCode());
        verify(inventoryClient, never()).reserve(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyString(), any(), any(), any());
    }

    // ── RC-6: eligibility lapsed between offer and commit ────────────────

    @Test
    void rc6_eligibilityLapsed_hardDenied_beforeAnyReservation() {
        when(eligibilityService.evaluate(any(), any(), any()))
                .thenReturn(ineligible(EligibilityService.CODE_LICENCE_NOT_ACTIVE));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-4", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_ELIGIBILITY_LAPSED, result.outcomeCode());
        assertEquals(OfferStatus.FAILED_REVALIDATION, offer.getStatus());
        verify(inventoryClient, never()).reserve(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyString(), any(), any(), any());
    }

    // ── RC-7: prescription claim fails — reservation compensated ─────────

    @Test
    void rc7_claimFails_reservationReleased_projectionMirrored() {
        when(orosClient.claimPrescription(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-5", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_CLAIM_FAILED, result.outcomeCode());
        // compensation: the DURA hold was released
        verify(inventoryClient).release(eq(DURA_RESERVATION), any());
        // projection mirrors the release — never disagrees with DURA
        assertEquals(ReservationStatus.RELEASED, savedProjection.get().getStatus());
        assertEquals(OfferStatus.FAILED_REVALIDATION, offer.getStatus());
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
    }

    // ── RC-1: idempotent double-select ───────────────────────────────────

    @Test
    void rc1_sameIdempotencyKey_replaysFirstResult_withoutReexecution() {
        SelectionEntity existing = new SelectionEntity();
        existing.setSelectionId("01SELAAAAAAAAAAAAAAAAAAAAA");
        existing.setTenantId(TENANT);
        existing.setRequestId(REQUEST_ID);
        existing.setOfferId(OFFER_ID);
        existing.setStatus(SelectionStatus.COMMITTED);
        existing.setIdempotencyKey("IDEM-1");
        when(selectionRepository.findFirstByTenantIdAndIdempotencyKey(TENANT, "IDEM-1"))
                .thenReturn(Optional.of(existing));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-1", null);

        assertTrue(result.replayed());
        assertTrue(result.committed());
        assertEquals("01SELAAAAAAAAAAAAAAAAAAAAA", result.selection().getSelectionId());
        verify(inventoryClient, never()).reserve(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyString(), any(), any(), any());
        verify(selectionRepository, never()).save(any());
    }

    @Test
    void rc1_differentKey_whileActiveSelectionExists_conflicts() {
        SelectionEntity live = new SelectionEntity();
        live.setSelectionId("01SELBBBBBBBBBBBBBBBBBBBBB");
        live.setStatus(SelectionStatus.COMMITTED);
        when(selectionRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of(live));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-OTHER", null));
        assertTrue(ex.getMessage().contains("SELECTION_EXISTS"));
    }

    @Test
    void rc1_failedSelection_doesNotBlockANewAttempt() {
        SelectionEntity failed = new SelectionEntity();
        failed.setSelectionId("01SELCCCCCCCCCCCCCCCCCCCCC");
        failed.setStatus(SelectionStatus.FAILED);
        when(selectionRepository.findByRequestId(REQUEST_ID)).thenReturn(List.of(failed));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-6", null);
        assertTrue(result.committed());
    }

    @Test
    void missingIdempotencyKey_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", null, null));
    }

    // ── §13.4 controlled gating at commit ────────────────────────────────

    @Test
    void controlled_unauthorisedVendor_hardFails() {
        request.setControlled(true);
        when(eligibilityService.hasControlledAuthority(vendor)).thenReturn(false);

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-7", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_CONTROLLED_VENDOR_UNAUTHORISED, result.outcomeCode());
        verify(inventoryClient, never()).reserve(any(), any(), anyString(), anyInt(), any(),
                anyString(), anyString(), any(), any(), any());
    }

    @Test
    void controlled_commit_writesControlledRegister() {
        request.setControlled(true);

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-8", null);

        assertTrue(result.committed());
        verify(inventoryClient).recordControlledIssue(eq(DURA_FACILITY), eq(DURA_STORE),
                eq("ZIBO-X"), eq(BigDecimal.valueOf(2)), any(), eq("PRV-100"),
                eq(result.selection().getSelectionId()), any());
    }

    @Test
    void controlled_registerWriteFails_commitmentFailsClosed_withFullCompensation() {
        request.setControlled(true);
        when(inventoryClient.recordControlledIssue(any(), any(), anyString(), any(), any(),
                any(), anyString(), any())).thenReturn(false);

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-9", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_CONTROLLED_REGISTER_WRITE_FAILED, result.outcomeCode());
        verify(inventoryClient).release(eq(DURA_RESERVATION), any());
        verify(orosClient).releaseClaim(eq("CLAIM-1"), anyString(), any());
        assertEquals(SelectionStatus.FAILED, result.selection().getStatus());
    }

    // ═════════ Finance lane — OF-B9 step 7 (financial clearance) ═════════

    @Test
    void step7_payerCovered_unverified_failsClosed_withCompensation() {
        // §10.4/§10.5: engines unreachable → UNVERIFIED → payer-covered commitment refused.
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(payerBlock(OfferFinancialsService.STATUS_UNVERIFIED, null, null,
                        false, OfferFinancialsService.PA_UNVERIFIED, null));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-F1", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_FINANCIAL_UNVERIFIED, result.outcomeCode());
        assertEquals(SelectionStatus.FAILED, result.selection().getStatus());
        // full compensation: DURA hold + prescription claim released
        verify(inventoryClient).release(eq(DURA_RESERVATION), any());
        verify(orosClient).releaseClaim(eq("CLAIM-1"), anyString(), any());
        // nothing was ever charged
        verify(mushexClient, never()).createSelectionPaymentIntent(any(), anyString(), any(), any());
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
    }

    @Test
    void step7_payerCovered_notCovered_failsClosed_withDistinctCode() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(payerBlock(OfferFinancialsService.STATUS_NOT_COVERED, null, null,
                        false, OfferFinancialsService.PA_NOT_REQUIRED, null));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-F2", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_NOT_COVERED, result.outcomeCode());
        verify(inventoryClient).release(eq(DURA_RESERVATION), any());
    }

    @Test
    void step7_selfPay_proceeds_despiteNoCoverageEngines() {
        // Explicit self-pay proceeds even when engines were never consulted (§10.4).
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(BigDecimal.ZERO));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-F3", null);

        assertTrue(result.committed());
        assertTrue(result.selection().getStepLogJson().contains("self-pay"));
    }

    // ═════════ OF-B8 — PA seam at step 7 ═════════

    @Test
    void step7_paRequired_notApproved_refusesCoded_paRequired() {
        UUID paRef = UUID.randomUUID();
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(payerBlock(OfferFinancialsService.STATUS_VERIFIED,
                        new BigDecimal("100.00"), BigDecimal.ZERO,
                        true, OfferFinancialsService.PA_NOT_APPROVED, paRef));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-F4", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_PA_REQUIRED, result.outcomeCode());
        // PA state recorded on the commitment (OF-B8 contract)
        assertEquals(OfferFinancialsService.PA_NOT_APPROVED, result.selection().getPaStatus());
        assertEquals(paRef.toString(), result.selection().getPaReference());
        verify(inventoryClient).release(eq(DURA_RESERVATION), any());
    }

    @Test
    void step7_paPending_alsoRefuses_conditionalOffer() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(payerBlock(OfferFinancialsService.STATUS_VERIFIED,
                        new BigDecimal("100.00"), BigDecimal.ZERO,
                        true, OfferFinancialsService.PA_PENDING, UUID.randomUUID()));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-F5", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_PA_REQUIRED, result.outcomeCode());
    }

    @Test
    void step7_paApproved_zeroShortfall_commitsWithWaivedPayment() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(payerBlock(OfferFinancialsService.STATUS_VERIFIED,
                        new BigDecimal("120.00"), BigDecimal.ZERO,
                        true, OfferFinancialsService.PA_APPROVED, UUID.randomUUID()));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-F6", null);

        assertTrue(result.committed());
        assertEquals(OfferFinancialsService.PA_APPROVED, result.selection().getPaStatus());
        assertTrue(result.selection().getStepLogJson().contains("WAIVED"));
        assertEquals(BigDecimal.ZERO, result.selection().getShortfallAmount());
        verify(mushexClient, never()).createSelectionPaymentIntent(any(), anyString(), any(), any());
    }

    // ═════════ OF-B10 — step 8 (payment) + resume + compensation ═════════

    @Test
    void step8_positiveShortfall_createsIntent_holdsAwaitingPayment() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(new BigDecimal("120.00")));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-P1", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_AWAITING_PAYMENT, result.outcomeCode());
        assertEquals(SelectionStatus.AWAITING_PAYMENT, result.selection().getStatus());
        assertEquals("mpi-1", result.selection().getPaymentIntentId());
        assertEquals(new BigDecimal("120.00"), result.selection().getShortfallAmount());
        verify(mushexClient).createSelectionPaymentIntent(any(),
                eq(result.selection().getSelectionId()), eq(new BigDecimal("120.00")), eq("ZWG"));
        // held — nothing committed, stock stays reserved, offer stays in-commit
        assertEquals(OfferStatus.REVALIDATING, offer.getStatus());
        assertEquals(MarketplaceRequestStatus.SELECTION_PENDING, request.getStatus());
        verify(inventoryClient, never()).release(any(), any());
        assertTrue(result.selection().getStepLogJson().contains("PENDING"));
    }

    @Test
    void step8_intentCreateFails_failsClosed_withCompensation() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(new BigDecimal("120.00")));
        when(mushexClient.createSelectionPaymentIntent(any(), anyString(), any(), any()))
                .thenReturn(Optional.empty());

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-P2", null);

        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_PAYMENT_INTENT_FAILED, result.outcomeCode());
        verify(inventoryClient).release(eq(DURA_RESERVATION), any());
        verify(orosClient).releaseClaim(eq("CLAIM-1"), anyString(), any());
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
    }

    @Test
    void paidCallback_resumesCommitment_toCommitted() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(new BigDecimal("120.00")));
        service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-P3", null);
        SelectionEntity held = savedSelection.get();
        when(selectionRepository.findFirstByPaymentIntentId("mpi-1")).thenReturn(Optional.of(held));

        Optional<CommitmentService.CommitResult> resumed =
                service.onPaymentStatusChanged("mpi-1", "PAID");

        assertTrue(resumed.isPresent());
        assertTrue(resumed.get().committed());
        assertEquals(CommitmentService.CODE_COMMITTED, resumed.get().outcomeCode());
        assertEquals(SelectionStatus.COMMITTED, held.getStatus());
        assertEquals("PAID", held.getPaymentStatus());
        assertEquals(OfferStatus.COMMITTED, offer.getStatus());
        assertEquals(MarketplaceRequestStatus.COMMITTED, request.getStatus());
        // the step log kept its history AND gained the PAID resolution
        assertTrue(held.getStepLogJson().contains("PAID"));
        assertTrue(held.getStepLogJson().contains("DURA_RESERVATION"));
        // CC-2: the losing offer release still ran through the guarded sequence
        assertEquals(OfferStatus.NOT_SELECTED, otherOffer.getStatus());
    }

    @Test
    void failedCallback_compensates_selectionFails_paymentFailed() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(new BigDecimal("120.00")));
        service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-P4", null);
        SelectionEntity held = savedSelection.get();
        when(selectionRepository.findFirstByPaymentIntentId("mpi-1")).thenReturn(Optional.of(held));

        Optional<CommitmentService.CommitResult> resumed =
                service.onPaymentStatusChanged("mpi-1", "FAILED");

        assertTrue(resumed.isPresent());
        assertFalse(resumed.get().committed());
        assertEquals(CommitmentService.CODE_PAYMENT_FAILED, resumed.get().outcomeCode());
        assertEquals(SelectionStatus.FAILED, held.getStatus());
        // RC-5 family compensation: hold + claim released; nothing was paid, so no refund
        verify(inventoryClient).release(eq(DURA_RESERVATION), any());
        verify(orosClient).releaseClaim(eq("CLAIM-1"), anyString(), any());
        verify(mushexClient, never()).tryRefund(any(), anyString(), any(), anyString());
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
    }

    @Test
    void interimCallback_pending_updatesProjectionOnly() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(new BigDecimal("120.00")));
        service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-P5", null);
        SelectionEntity held = savedSelection.get();
        when(selectionRepository.findFirstByPaymentIntentId("mpi-1")).thenReturn(Optional.of(held));

        Optional<CommitmentService.CommitResult> resumed =
                service.onPaymentStatusChanged("mpi-1", "PENDING");

        assertTrue(resumed.isEmpty());
        assertEquals(SelectionStatus.AWAITING_PAYMENT, held.getStatus());
        assertEquals("PENDING", held.getPaymentStatus());
        verify(inventoryClient, never()).release(any(), any());
    }

    @Test
    void unknownIntentCallback_isIgnored() {
        when(selectionRepository.findFirstByPaymentIntentId("mpi-unknown"))
                .thenReturn(Optional.empty());
        assertTrue(service.onPaymentStatusChanged("mpi-unknown", "PAID").isEmpty());
    }

    @Test
    void rc5_controlledWriteFailsAfterPaid_refundsAutomatically() {
        request.setControlled(true);
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(new BigDecimal("120.00")));
        service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-P6", null);
        SelectionEntity held = savedSelection.get();
        assertEquals(SelectionStatus.AWAITING_PAYMENT, held.getStatus());
        when(selectionRepository.findFirstByPaymentIntentId("mpi-1")).thenReturn(Optional.of(held));
        // §13.4 register write fails during the PAID resume
        when(inventoryClient.recordControlledIssue(any(), any(), anyString(), any(), any(),
                any(), anyString(), any())).thenReturn(false);

        Optional<CommitmentService.CommitResult> resumed =
                service.onPaymentStatusChanged("mpi-1", "PAID");

        assertTrue(resumed.isPresent());
        assertEquals(CommitmentService.CODE_CONTROLLED_REGISTER_WRITE_FAILED, resumed.get().outcomeCode());
        // RC-5: the PAID money comes back — automatically and visibly
        verify(mushexClient).tryRefund(any(), eq("mpi-1"), eq(new BigDecimal("120.00")), anyString());
        verify(inventoryClient).release(eq(DURA_RESERVATION), any());
        verify(orosClient).releaseClaim(eq("CLAIM-1"), anyString(), any());
    }

    @Test
    void sweep_awaitingPaymentPastReservationTtl_timesOutWithCompensation() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(new BigDecimal("120.00")));
        service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-P7", null);
        SelectionEntity held = savedSelection.get();
        when(selectionRepository.findByStatus(SelectionStatus.AWAITING_PAYMENT))
                .thenReturn(List.of(held));
        offer.setTtlExpiresAt(OffsetDateTime.now().minusMinutes(1));

        int swept = service.sweepAwaitingPayment(OffsetDateTime.now());

        assertEquals(1, swept);
        assertEquals(SelectionStatus.FAILED, held.getStatus());
        assertEquals(CommitmentService.CODE_PAYMENT_TIMEOUT, held.getFailureCode());
        verify(inventoryClient).release(eq(DURA_RESERVATION), any());
        verify(orosClient).releaseClaim(eq("CLAIM-1"), anyString(), any());
        assertEquals(MarketplaceRequestStatus.OFFERS_AVAILABLE, request.getStatus());
    }

    @Test
    void sweep_leavesLiveRetryWindowsAlone() {
        when(offerFinancialsService.compute(any(), any(), any(), any()))
                .thenReturn(selfPayBlock(new BigDecimal("120.00")));
        service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-P8", null);
        SelectionEntity held = savedSelection.get();
        when(selectionRepository.findByStatus(SelectionStatus.AWAITING_PAYMENT))
                .thenReturn(List.of(held));
        offer.setTtlExpiresAt(OffsetDateTime.now().plusHours(1));

        assertEquals(0, service.sweepAwaitingPayment(OffsetDateTime.now()));
        assertEquals(SelectionStatus.AWAITING_PAYMENT, held.getStatus());
    }

    @Test
    void rc1_replayOfHeldSelection_returnsAwaitingPayment() {
        SelectionEntity held = new SelectionEntity();
        held.setSelectionId("01SELDDDDDDDDDDDDDDDDDDDDD");
        held.setTenantId(TENANT);
        held.setRequestId(REQUEST_ID);
        held.setOfferId(OFFER_ID);
        held.setStatus(SelectionStatus.AWAITING_PAYMENT);
        held.setIdempotencyKey("IDEM-P9");
        when(selectionRepository.findFirstByTenantIdAndIdempotencyKey(TENANT, "IDEM-P9"))
                .thenReturn(Optional.of(held));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-P9", null);

        assertTrue(result.replayed());
        assertFalse(result.committed());
        assertEquals(CommitmentService.CODE_AWAITING_PAYMENT, result.outcomeCode());
    }

    // ── OF-B17: step-11 fulfilment dispatch ──────────────────────────────

    @Test
    void step11_deliveryPathway_dispatchesRealNhumeDelivery_recordedInStepLog() {
        request.setFulfilmentPathway(zw.gov.mohcc.impilo.msikaflow.domain.FulfilmentPathway.DELIVERY);
        when(fulfilmentDispatchService.dispatchOnCommit(any(), eq(request), eq(offer), any(), any()))
                .thenAnswer(inv -> {
                    SelectionEntity sel = inv.getArgument(0);
                    sel.setDispatchRef("NHM-DEL-1");
                    sel.setDispatchStatus(FulfilmentDispatchService.DISPATCH_DISPATCHED);
                    return new FulfilmentDispatchService.DispatchOutcome(
                            FulfilmentDispatchService.DISPATCH_DISPATCHED, null,
                            "nhume delivery NHM-DEL-1 (required PoD grade OTP_MATCH)");
                });

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-D1", null);

        assertTrue(result.committed());
        assertEquals("NHM-DEL-1", result.selection().getDispatchRef());
        assertEquals(FulfilmentDispatchService.DISPATCH_DISPATCHED,
                result.selection().getDispatchStatus());
        assertTrue(result.selection().getStepLogJson().contains("FULFILMENT_DISPATCH"));
        assertTrue(result.selection().getStepLogJson().contains("NHM-DEL-1"));
        verify(fulfilmentDispatchService).dispatchOnCommit(any(), eq(request), eq(offer), any(), any());
    }

    @Test
    void step11_dispatchFailure_neverRollsBackTheCommitment() {
        request.setFulfilmentPathway(zw.gov.mohcc.impilo.msikaflow.domain.FulfilmentPathway.DELIVERY);
        when(fulfilmentDispatchService.dispatchOnCommit(any(), any(), any(), any(), any()))
                .thenReturn(new FulfilmentDispatchService.DispatchOutcome(
                        FulfilmentDispatchService.DISPATCH_FAILED,
                        FulfilmentDispatchService.CODE_NHUME_UNAVAILABLE,
                        "Nhume delivery create refused/unreachable — retry sweep will re-attempt"));

        CommitmentService.CommitResult result =
                service.commit(REQUEST_ID, OFFER_ID, TENANT, "patient-1", "IDEM-D2", null);

        // The order stands (RC-8/step-11 doctrine): COMMITTED with the honest
        // coded PARTIAL entry; holds and claim are NOT compensated.
        assertTrue(result.committed());
        assertEquals(SelectionStatus.COMMITTED, result.selection().getStatus());
        assertTrue(result.selection().getStepLogJson().contains("PARTIAL"));
        assertTrue(result.selection().getStepLogJson().contains(
                FulfilmentDispatchService.CODE_NHUME_UNAVAILABLE));
        verify(inventoryClient, never()).release(any(), any());
        verify(orosClient, never()).releaseClaim(anyString(), anyString(), any());
        verify(mushexClient, never()).tryRefund(any(), anyString(), any(), anyString());
    }
}
