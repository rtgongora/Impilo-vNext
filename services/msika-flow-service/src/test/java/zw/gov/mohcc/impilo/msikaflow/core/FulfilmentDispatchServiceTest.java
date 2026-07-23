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
import zw.gov.mohcc.impilo.msikaflow.domain.FulfilmentPathway;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceProfile;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.PublicationMode;
import zw.gov.mohcc.impilo.msikaflow.domain.SelectionStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.StockGrade;
import zw.gov.mohcc.impilo.msikaflow.integration.MushexClient;
import zw.gov.mohcc.impilo.msikaflow.integration.NhumeClient;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OF-B17/OF-B18 — step-11 dispatch (RC-8 idempotency, §12.2 courier-minimum
 * PII contract, DISPATCH_FAILED never-rollback, retry sweep) and the Nhume
 * delivery-status write-back reception (escrow seam on DELIVERED, §12.7
 * refund-on-RETURNED, fail-closed delivery-id cross-check).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FulfilmentDispatchServiceTest {

    @Mock private SelectionRepository selectionRepository;
    @Mock private MarketplaceRequestRepository requestRepository;
    @Mock private FulfillmentOfferRepository offerRepository;
    @Mock private FulfillmentOfferLineRepository offerLineRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private NhumeClient nhumeClient;
    @Mock private MushexClient mushexClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private FulfilmentDispatchService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SELECTION_ID = "01SELAAAAAAAAAAAAAAAAAAAAA";
    private static final String REQUEST_ID = "01REQAAAAAAAAAAAAAAAAAAAAA";
    private static final String OFFER_ID = "01OFFAAAAAAAAAAAAAAAAAAAAA";
    private static final String VENDOR_ID = "01VENAAAAAAAAAAAAAAAAAAAAA";

    private SelectionEntity selection;
    private MarketplaceRequestEntity request;
    private FulfillmentOfferEntity offer;
    private FulfillmentOfferLineEntity line;

    @BeforeEach
    void setUp() {
        service = new FulfilmentDispatchService(selectionRepository, requestRepository,
                offerRepository, offerLineRepository, outboxRepository,
                nhumeClient, mushexClient, mapper);

        selection = new SelectionEntity();
        selection.setSelectionId(SELECTION_ID);
        selection.setTenantId(TENANT);
        selection.setRequestId(REQUEST_ID);
        selection.setOfferId(OFFER_ID);
        selection.setStatus(SelectionStatus.COMMITTED);
        selection.setIdempotencyKey("IDEM-1");
        selection.setPrescriptionClaimId("CLAIM-1");

        request = new MarketplaceRequestEntity();
        request.setRequestId(REQUEST_ID);
        request.setTenantId(TENANT);
        request.setOrosOrderId("OROS-1");
        request.setOrosOrderVersionId("OV-1");
        request.setProfile(MarketplaceProfile.MEDICATION);
        request.setPublicationMode(PublicationMode.INVITED);
        request.setStatus(MarketplaceRequestStatus.COMMITTED);
        request.setFulfilmentPathway(FulfilmentPathway.DELIVERY);
        request.setDeliveryDetailsJson(
                "{\"name\":\"Tariro Moyo\",\"phone\":\"+263771234567\",\"address\":\"12 Herbert Chitepo Ave, Harare\"}");
        request.setPublishedLinesJson("{\"lines\":[]}");

        offer = new FulfillmentOfferEntity();
        offer.setOfferId(OFFER_ID);
        offer.setTenantId(TENANT);
        offer.setRequestId(REQUEST_ID);
        offer.setVendorId(VENDOR_ID);
        offer.setPriceTotal(new BigDecimal("50.00"));
        offer.setCurrency("USD");

        line = new FulfillmentOfferLineEntity();
        line.setOfferLineId("01LINAAAAAAAAAAAAAAAAAAAAA");
        line.setOfferId(OFFER_ID);
        line.setItemCode("ZIBO-SECRET-MED");
        line.setQuantity(2);
        line.setStockGrade(StockGrade.VERIFIED);

        when(selectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(selectionRepository.findById(SELECTION_ID)).thenReturn(Optional.of(selection));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(nhumeClient.createCommitmentDelivery(any(), any()))
                .thenReturn(Optional.of(new NhumeClient.NhumeDeliveryCreated("NHM-DEL-1", "SUBMITTED")));
    }

    // ── dispatch ─────────────────────────────────────────────────────────

    @Test
    void deliveryPathway_dispatchesWithCourierMinimumSpec() {
        FulfilmentDispatchService.DispatchOutcome outcome =
                service.dispatchOnCommit(selection, request, offer, List.of(line), null);

        assertTrue(outcome.dispatched());
        assertEquals("NHM-DEL-1", selection.getDispatchRef());
        assertEquals(FulfilmentDispatchService.DISPATCH_DISPATCHED, selection.getDispatchStatus());

        ArgumentCaptor<NhumeClient.CommitmentDeliverySpec> captor =
                ArgumentCaptor.forClass(NhumeClient.CommitmentDeliverySpec.class);
        verify(nhumeClient).createCommitmentDelivery(captor.capture(), any());
        NhumeClient.CommitmentDeliverySpec spec = captor.getValue();
        assertEquals(SELECTION_ID, spec.selectionId());
        assertEquals(VENDOR_ID, spec.vendorId());
        assertEquals("Tariro Moyo", spec.recipientName());
        assertEquals("12 Herbert Chitepo Ave, Harare", spec.deliveryAddress());
        assertEquals("CLAIM-1", spec.dispenseEpisodeRef());
        assertEquals("OTP_MATCH", spec.requiredPodGrade());
        // §12.2/CC-9 — cargo lines are OPAQUE offer-line refs, never item codes.
        assertEquals(1, spec.cargoLines().size());
        assertEquals("01LINAAAAAAAAAAAAAAAAAAAAA", spec.cargoLines().get(0).offerLineRef());
    }

    @Test
    void courierBody_neverCarriesItemCodesOrClinicalContent() {
        FulfilmentDispatchService.DispatchOutcome outcome =
                service.dispatchOnCommit(selection, request, offer, List.of(line), null);
        assertTrue(outcome.dispatched());

        ArgumentCaptor<NhumeClient.CommitmentDeliverySpec> captor =
                ArgumentCaptor.forClass(NhumeClient.CommitmentDeliverySpec.class);
        verify(nhumeClient).createCommitmentDelivery(captor.capture(), any());
        Map<String, Object> body = NhumeClient.commitmentDeliveryBody(captor.getValue());
        String json = assertDoesNotThrow(() -> mapper.writeValueAsString(body));
        // The ZIBO item code must NEVER reach the courier payload (§12.2, CC-9).
        assertFalse(json.contains("ZIBO-SECRET-MED"));
        assertFalse(json.contains("diagnosis"));
        assertTrue(json.contains("MARKETPLACE_COMMITMENT"));
        assertTrue(json.contains("msikaFlowSelectionRef"));
        assertTrue(json.contains("named_recipient_required"));
    }

    @Test
    void controlledRequest_demandsSecondFactorGrade() {
        request.setControlled(true);
        service.dispatchOnCommit(selection, request, offer, List.of(line), null);

        ArgumentCaptor<NhumeClient.CommitmentDeliverySpec> captor =
                ArgumentCaptor.forClass(NhumeClient.CommitmentDeliverySpec.class);
        verify(nhumeClient).createCommitmentDelivery(captor.capture(), any());
        assertEquals("OTP_PLUS_ID", captor.getValue().requiredPodGrade());
        assertTrue(captor.getValue().controlled());
    }

    @Test
    void rc8_replayWithExistingDispatchRef_returnsWithoutSecondDispatch() {
        selection.setDispatchRef("NHM-DEL-1");
        FulfilmentDispatchService.DispatchOutcome outcome =
                service.dispatchOnCommit(selection, request, offer, List.of(line), null);

        assertTrue(outcome.dispatched());
        verify(nhumeClient, never()).createCommitmentDelivery(any(), any());
    }

    @Test
    void pickupOrNullPathway_needsNoDispatch_failClosed() {
        request.setFulfilmentPathway(null);
        FulfilmentDispatchService.DispatchOutcome outcome =
                service.dispatchOnCommit(selection, request, offer, List.of(line), null);

        assertEquals(FulfilmentDispatchService.DISPATCH_NOT_REQUIRED, outcome.status());
        verify(nhumeClient, never()).createCommitmentDelivery(any(), any());

        request.setFulfilmentPathway(FulfilmentPathway.PICKUP);
        assertEquals(FulfilmentDispatchService.DISPATCH_NOT_REQUIRED,
                service.dispatchOnCommit(selection, request, offer, List.of(line), null).status());
    }

    @Test
    void deliveryElectionWithoutDetails_failsClosedCoded_commitmentUntouched() {
        request.setDeliveryDetailsJson(null);
        FulfilmentDispatchService.DispatchOutcome outcome =
                service.dispatchOnCommit(selection, request, offer, List.of(line), null);

        assertTrue(outcome.failed());
        assertEquals(FulfilmentDispatchService.CODE_DELIVERY_DETAILS_MISSING, outcome.code());
        assertEquals(FulfilmentDispatchService.DISPATCH_FAILED, selection.getDispatchStatus());
        assertEquals(SelectionStatus.COMMITTED, selection.getStatus());
        verify(nhumeClient, never()).createCommitmentDelivery(any(), any());
    }

    @Test
    void nhumeFailure_isCodedOutcome_neverThrows() {
        when(nhumeClient.createCommitmentDelivery(any(), any())).thenReturn(Optional.empty());

        FulfilmentDispatchService.DispatchOutcome outcome = assertDoesNotThrow(() ->
                service.dispatchOnCommit(selection, request, offer, List.of(line), null));

        assertTrue(outcome.failed());
        assertEquals(FulfilmentDispatchService.CODE_NHUME_UNAVAILABLE, outcome.code());
        assertEquals(FulfilmentDispatchService.DISPATCH_FAILED, selection.getDispatchStatus());
        assertNull(selection.getDispatchRef());
    }

    @Test
    void retrySweep_reDispatchesFailedCommittedSelections() {
        selection.setDispatchStatus(FulfilmentDispatchService.DISPATCH_FAILED);
        selection.setDispatchFailureCode(FulfilmentDispatchService.CODE_NHUME_UNAVAILABLE);
        when(selectionRepository.findByStatusAndDispatchStatus(
                SelectionStatus.COMMITTED, FulfilmentDispatchService.DISPATCH_FAILED))
                .thenReturn(List.of(selection));
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
        when(offerLineRepository.findByOfferId(OFFER_ID)).thenReturn(List.of(line));

        int retried = service.retryFailedDispatches();

        assertEquals(1, retried);
        assertEquals("NHM-DEL-1", selection.getDispatchRef());
        assertEquals(FulfilmentDispatchService.DISPATCH_DISPATCHED, selection.getDispatchStatus());
        assertNull(selection.getDispatchFailureCode());
    }

    // ── delivery-status write-back reception ─────────────────────────────

    @Test
    void delivered_projectsStatus_recordsGrade_marksEscrowSeamPending_whenPaid() {
        selection.setDispatchRef("NHM-DEL-1");
        selection.setPaymentIntentId("mpi-1");
        selection.setPaymentStatus("PAID");
        selection.setShortfallAmount(new BigDecimal("30.00"));
        selection.setShortfallCurrency("USD");
        selection.setStepLogJson("[]");

        Optional<SelectionEntity> updated = service.onDeliveryStatus(
                SELECTION_ID, "DELIVERED", "NHM-DEL-1", "proof-9", "OTP_MATCH", null);

        assertTrue(updated.isPresent());
        assertEquals("DELIVERED", updated.get().getDeliveryStatus());
        assertEquals("proof-9", updated.get().getDeliveryProofRef());
        assertEquals("OTP_MATCH", updated.get().getDeliveryVerificationGrade());
        // OF-B18 escrow seam: honest deferral marker, never a fabricated release.
        assertEquals(FulfilmentDispatchService.ESCROW_RELEASE_PENDING,
                updated.get().getEscrowReleaseStatus());
        assertTrue(updated.get().getStepLogJson().contains("FULFILMENT_DELIVERED"));

        ArgumentCaptor<EventOutboxEntity> events = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(events.capture());
        assertTrue(events.getAllValues().stream()
                .anyMatch(e -> "SELECTION_ESCROW_RELEASE_PENDING".equals(e.getEventType())));
    }

    @Test
    void delivered_unpaidSelection_escrowNotApplicable() {
        selection.setDispatchRef("NHM-DEL-1");

        Optional<SelectionEntity> updated = service.onDeliveryStatus(
                SELECTION_ID, "DELIVERED", "NHM-DEL-1", null, "SIGNATURE_ONLY", null);

        assertTrue(updated.isPresent());
        assertEquals(FulfilmentDispatchService.ESCROW_NOT_APPLICABLE,
                updated.get().getEscrowReleaseStatus());
        verify(mushexClient, never()).tryRefund(any(), anyString(), any(), anyString());
    }

    @Test
    void returned_paidSelection_requestsMushexRefund() {
        selection.setDispatchRef("NHM-DEL-1");
        selection.setPaymentIntentId("mpi-1");
        selection.setPaymentStatus("PAID");
        selection.setShortfallAmount(new BigDecimal("30.00"));
        when(mushexClient.tryRefund(any(), eq("mpi-1"), eq(new BigDecimal("30.00")), anyString()))
                .thenReturn(Optional.of(new MushexClient.MushexRefundCreated("rf-1", "PENDING")));

        Optional<SelectionEntity> updated = service.onDeliveryStatus(
                SELECTION_ID, "RETURNED", "NHM-DEL-1", null, null, null);

        assertTrue(updated.isPresent());
        assertEquals("rf-1", updated.get().getRefundRef());
        verify(mushexClient).tryRefund(any(), eq("mpi-1"), eq(new BigDecimal("30.00")),
                eq("DELIVERY_RETURNED:" + SELECTION_ID));
        ArgumentCaptor<EventOutboxEntity> events = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(events.capture());
        assertTrue(events.getAllValues().stream()
                .anyMatch(e -> "SELECTION_RETURN_REFUND_REQUESTED".equals(e.getEventType())));
    }

    @Test
    void returned_refundTransportFailure_isEventedLoud() {
        selection.setDispatchRef("NHM-DEL-1");
        selection.setPaymentIntentId("mpi-1");
        selection.setPaymentStatus("PAID");
        selection.setShortfallAmount(new BigDecimal("30.00"));
        when(mushexClient.tryRefund(any(), anyString(), any(), anyString()))
                .thenReturn(Optional.empty());

        service.onDeliveryStatus(SELECTION_ID, "RETURNED", "NHM-DEL-1", null, null, null);

        ArgumentCaptor<EventOutboxEntity> events = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(events.capture());
        assertTrue(events.getAllValues().stream()
                .anyMatch(e -> "SELECTION_RETURN_REFUND_FAILED".equals(e.getEventType())));
        assertNull(selection.getRefundRef());
    }

    @Test
    void deliveryIdMismatch_isRejectedFailClosed() {
        selection.setDispatchRef("NHM-DEL-1");

        Optional<SelectionEntity> updated = service.onDeliveryStatus(
                SELECTION_ID, "DELIVERED", "NHM-DEL-SPOOFED", "p", "OTP_MATCH", null);

        assertTrue(updated.isEmpty());
        assertNull(selection.getDeliveryStatus());
        verify(mushexClient, never()).tryRefund(any(), anyString(), any(), anyString());
    }

    @Test
    void unknownSelection_isRejected() {
        when(selectionRepository.findById("NOPE")).thenReturn(Optional.empty());
        assertTrue(service.onDeliveryStatus("NOPE", "DELIVERED", "NHM-DEL-1", null, null, null).isEmpty());
    }

    @Test
    void nonTerminalStatus_isProjectionOnly() {
        selection.setDispatchRef("NHM-DEL-1");

        Optional<SelectionEntity> updated = service.onDeliveryStatus(
                SELECTION_ID, "PICKED_UP", "NHM-DEL-1", null, null, null);

        assertTrue(updated.isPresent());
        assertEquals("PICKED_UP", updated.get().getDeliveryStatus());
        assertNull(updated.get().getEscrowReleaseStatus());
        verify(mushexClient, never()).tryRefund(any(), anyString(), any(), anyString());
    }

    // ── pathway election ─────────────────────────────────────────────────

    @Test
    void electDelivery_requiresTheDeliveryMinimumBlock() {
        when(requestRepository.findByRequestIdAndTenantId(REQUEST_ID, TENANT))
                .thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(IllegalArgumentException.class, () -> service.electPathway(
                REQUEST_ID, TENANT, FulfilmentPathway.DELIVERY, "Tariro Moyo", null, null, null, null));

        MarketplaceRequestEntity updated = service.electPathway(
                REQUEST_ID, TENANT, FulfilmentPathway.DELIVERY,
                "Tariro Moyo", "+263771234567", "12 Herbert Chitepo Ave", null, null);
        assertEquals(FulfilmentPathway.DELIVERY, updated.getFulfilmentPathway());
        assertNotNull(updated.getDeliveryDetailsJson());
        assertTrue(updated.getDeliveryDetailsJson().contains("Tariro Moyo"));
    }
}
