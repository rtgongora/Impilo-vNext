package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.FulfilmentPathway;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.SelectionStatus;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OF-B17/OF-B18 — the §8.9 step-11 fulfilment-dispatch seam plus the Nhume
 * delivery-status write-back reception for marketplace commitments.
 *
 * <p><b>Dispatch (§12.1):</b> a COMMITTED selection whose request elected the
 * DELIVERY pathway gets a real Nhume delivery task — idempotent on the
 * selection id (RC-8: {@code dispatch_ref} stores the Nhume delivery id;
 * replay returns it). Dispatch failure NEVER rolls back the commitment: the
 * order stands with the honest coded {@code DISPATCH_FAILED} outcome and the
 * retry sweep re-attempts. PICKUP-pathway (and NULL — fail-closed default)
 * selections need no delivery task: for medication profiles the step-6 OROS
 * claim already spawned the pharmacy dispense episode (§8.10 claim semantics).</p>
 *
 * <p><b>Write-back (§12.8):</b> Nhume reports status changes through its
 * integration write-back channel (Nhume's outbox currently has NO Kafka
 * publisher — the HTTP write-back IS the live channel; a Kafka consumer lane
 * is deferred until Nhume gains an outbox relay). Terminal facts:</p>
 * <ul>
 *   <li>{@code DELIVERED} (PoD-verified, §12.4 grade attached) → step-log
 *       completion + the OF-B18 escrow seam: selection payments ride MusheX
 *       intent→PAID with NO wallet-escrow hold today (§10.7 no-two-phase-
 *       capture; mushe-wallet escrow exists only for crowdfunding campaigns),
 *       so release is recorded {@code ESCROW_RELEASE_PENDING} with a coded
 *       event — an honest deferral marker, never a fabricated release.</li>
 *   <li>{@code RETURNED} (§12.7) → refund-on-return via the existing MusheX
 *       refund path for a PAID shortfall; the refund outcome (or its failure)
 *       is evented loud, never swallowed.</li>
 * </ul>
 */
@Service
public class FulfilmentDispatchService {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentDispatchService.class);

    public static final String DISPATCH_DISPATCHED = "DISPATCHED";
    public static final String DISPATCH_FAILED = "DISPATCH_FAILED";
    public static final String DISPATCH_NOT_REQUIRED = "NOT_REQUIRED";

    public static final String CODE_DELIVERY_DETAILS_MISSING = "DELIVERY_DETAILS_MISSING";
    public static final String CODE_NHUME_UNAVAILABLE = "NHUME_UNAVAILABLE";

    public static final String ESCROW_RELEASE_PENDING = "ESCROW_RELEASE_PENDING";
    public static final String ESCROW_NOT_APPLICABLE = "NOT_APPLICABLE";

    /** Outcome of one step-11 dispatch attempt. */
    public record DispatchOutcome(String status, String code, String detail) {
        public boolean dispatched() { return DISPATCH_DISPATCHED.equals(status); }
        public boolean failed() { return DISPATCH_FAILED.equals(status); }
    }

    private final SelectionRepository selectionRepository;
    private final MarketplaceRequestRepository requestRepository;
    private final FulfillmentOfferRepository offerRepository;
    private final FulfillmentOfferLineRepository offerLineRepository;
    private final EventOutboxRepository outboxRepository;
    private final NhumeClient nhumeClient;
    private final MushexClient mushexClient;
    private final ObjectMapper objectMapper;

    public FulfilmentDispatchService(SelectionRepository selectionRepository,
                                     MarketplaceRequestRepository requestRepository,
                                     FulfillmentOfferRepository offerRepository,
                                     FulfillmentOfferLineRepository offerLineRepository,
                                     EventOutboxRepository outboxRepository,
                                     NhumeClient nhumeClient,
                                     MushexClient mushexClient,
                                     ObjectMapper objectMapper) {
        this.selectionRepository = selectionRepository;
        this.requestRepository = requestRepository;
        this.offerRepository = offerRepository;
        this.offerLineRepository = offerLineRepository;
        this.outboxRepository = outboxRepository;
        this.nhumeClient = nhumeClient;
        this.mushexClient = mushexClient;
        this.objectMapper = objectMapper;
    }

    // ── step-11 dispatch ─────────────────────────────────────────────────

    /**
     * Dispatch the fulfilment for a selection that just reached COMMITTED.
     * Never throws — a dispatch problem is a coded outcome, not a commitment
     * rollback (the clinical order and the commitment stand; §8.9 step 11).
     */
    public DispatchOutcome dispatchOnCommit(SelectionEntity selection, MarketplaceRequestEntity request,
                                            FulfillmentOfferEntity offer,
                                            List<FulfillmentOfferLineEntity> lines,
                                            HttpServletRequest inbound) {
        try {
            // RC-8 — idempotent replay: an existing dispatch ref IS the answer.
            if (selection.getDispatchRef() != null && !selection.getDispatchRef().isBlank()) {
                return new DispatchOutcome(DISPATCH_DISPATCHED, null,
                        "replay — delivery " + selection.getDispatchRef() + " already dispatched");
            }
            FulfilmentPathway pathway = request.getFulfilmentPathway();
            if (pathway != FulfilmentPathway.DELIVERY) {
                // NULL ⇒ PICKUP, fail-closed. Medication pickup fulfilment rides the
                // step-6 OROS claim (dispense episode already spawned pharmacy-side).
                selection.setDispatchStatus(DISPATCH_NOT_REQUIRED);
                return new DispatchOutcome(DISPATCH_NOT_REQUIRED, null,
                        (pathway == null ? "no pathway election — PICKUP assumed (fail-closed)"
                                : "PICKUP pathway")
                                + (selection.getPrescriptionClaimId() != null
                                        ? "; dispense episode rides OROS claim "
                                                + selection.getPrescriptionClaimId()
                                        : ""));
            }
            DeliveryDetails details = parseDeliveryDetails(request.getDeliveryDetailsJson());
            if (details == null) {
                // Fail-closed: a DELIVERY election without the §11.8 delivery-minimum
                // block can never be dispatched — but the commitment stands.
                return recordDispatchFailure(selection, request, CODE_DELIVERY_DETAILS_MISSING,
                        "DELIVERY pathway elected but delivery details (name/contact/address) absent");
            }
            List<NhumeClient.CommitmentCargoLine> cargo = new ArrayList<>();
            boolean coldChain = false;
            for (FulfillmentOfferLineEntity line : lines) {
                cargo.add(new NhumeClient.CommitmentCargoLine(
                        line.getOfferLineId(), line.getQuantity(), request.isControlled(), false));
            }
            String requiredGrade = request.isControlled() ? "OTP_PLUS_ID" : "OTP_MATCH";
            NhumeClient.CommitmentDeliverySpec spec = new NhumeClient.CommitmentDeliverySpec(
                    selection.getSelectionId(),
                    request.getRequestId(),
                    selection.getTenantId(),
                    offer.getVendorId(),
                    request.getOrosOrderId(),
                    selection.getPrescriptionClaimId(),
                    request.isControlled(),
                    coldChain,
                    details.name(),
                    details.phone(),
                    details.address(),
                    details.lat(),
                    details.lng(),
                    requiredGrade,
                    "PAID".equalsIgnoreCase(selection.getPaymentStatus())
                            ? selection.getPaymentIntentId() : null,
                    cargo);
            Optional<NhumeClient.NhumeDeliveryCreated> created =
                    nhumeClient.createCommitmentDelivery(spec, inbound);
            if (created.isEmpty()) {
                return recordDispatchFailure(selection, request, CODE_NHUME_UNAVAILABLE,
                        "Nhume delivery create refused/unreachable — retry sweep will re-attempt");
            }
            selection.setDispatchRef(created.get().deliveryId());
            selection.setDispatchStatus(DISPATCH_DISPATCHED);
            selection.setDispatchFailureCode(null);
            selection.setDeliveryStatus(created.get().status());
            selection.setDeliveryStatusAt(OffsetDateTime.now());
            publishEvent(selection, "SELECTION_DISPATCHED", Map.of(
                    "deliveryId", created.get().deliveryId(),
                    "requiredPodGrade", requiredGrade));
            log.info("Marketplace selection {} dispatched: nhume delivery {}",
                    selection.getSelectionId(), created.get().deliveryId());
            return new DispatchOutcome(DISPATCH_DISPATCHED, null,
                    "nhume delivery " + created.get().deliveryId()
                            + " (required PoD grade " + requiredGrade + ")");
        } catch (Exception e) {
            // Belt-and-braces: NOTHING thrown from step 11 may fail the commitment.
            log.error("Unexpected dispatch error for selection {}: {}",
                    selection.getSelectionId(), e.toString());
            return recordDispatchFailure(selection, request, CODE_NHUME_UNAVAILABLE,
                    "unexpected dispatch error: " + e.getClass().getSimpleName());
        }
    }

    private DispatchOutcome recordDispatchFailure(SelectionEntity selection,
                                                  MarketplaceRequestEntity request,
                                                  String code, String detail) {
        selection.setDispatchStatus(DISPATCH_FAILED);
        selection.setDispatchFailureCode(code);
        publishEvent(selection, "SELECTION_DISPATCH_FAILED", Map.of(
                "code", code, "detail", detail, "requestId", request.getRequestId()));
        log.warn("Marketplace selection {} dispatch failed ({}): {}",
                selection.getSelectionId(), code, detail);
        return new DispatchOutcome(DISPATCH_FAILED, code, detail);
    }

    /**
     * OF-B17 retry sweep — COMMITTED selections stuck at DISPATCH_FAILED are
     * re-dispatched (idempotent by construction: RC-8 selection-stable key +
     * dispatch_ref replay guard). {@code DELIVERY_DETAILS_MISSING} rows are
     * retried too: the details may have been supplied since.
     */
    @Scheduled(fixedDelayString = "${msika-flow.marketplace.dispatch-retry-interval-ms:300000}")
    @Transactional
    public int retryFailedDispatches() {
        int retried = 0;
        for (SelectionEntity selection : selectionRepository
                .findByStatusAndDispatchStatus(SelectionStatus.COMMITTED, DISPATCH_FAILED)) {
            MarketplaceRequestEntity request = requestRepository.findById(selection.getRequestId()).orElse(null);
            FulfillmentOfferEntity offer = offerRepository.findById(selection.getOfferId()).orElse(null);
            if (request == null || offer == null) {
                continue;
            }
            List<FulfillmentOfferLineEntity> lines = offerLineRepository.findByOfferId(selection.getOfferId());
            DispatchOutcome outcome = dispatchOnCommit(selection, request, offer, lines, null);
            selectionRepository.save(selection);
            if (outcome.dispatched()) {
                retried++;
            }
        }
        return retried;
    }

    // ── fulfilment-pathway election (additive intake — §11.8 post-commitment column) ──

    /**
     * Record the patient's fulfilment-pathway election + delivery-minimum
     * details on the request row. DELIVERY demands name+phone+address
     * (fail-closed); the block is request-row-only truth, never published.
     */
    @Transactional
    public MarketplaceRequestEntity electPathway(String requestId, java.util.UUID tenantId,
                                                 FulfilmentPathway pathway,
                                                 String name, String phone, String address,
                                                 Double lat, Double lng) {
        MarketplaceRequestEntity request = requestRepository.findByRequestIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Marketplace request not found: " + requestId));
        if (request.getStatus() == MarketplaceRequestStatus.CANCELLED
                || request.getStatus() == MarketplaceRequestStatus.EXPIRED) {
            throw new IllegalStateException("REQUEST_TERMINAL: pathway cannot change on " + request.getStatus());
        }
        if (pathway == FulfilmentPathway.DELIVERY) {
            if (isBlank(name) || isBlank(phone) || isBlank(address)) {
                throw new IllegalArgumentException(
                        "DELIVERY_DETAILS_REQUIRED: DELIVERY election needs name, phone and address (§11.8)");
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("name", name);
            details.put("phone", phone);
            details.put("address", address);
            if (lat != null) details.put("lat", lat);
            if (lng != null) details.put("lng", lng);
            request.setDeliveryDetailsJson(JsonSupport.toJsonSafe(objectMapper, details, null));
        }
        request.setFulfilmentPathway(pathway);
        requestRepository.save(request);
        log.info("Marketplace request {} pathway elected: {}", requestId, pathway);
        return request;
    }

    // ── Nhume delivery-status write-back reception (§12.8) ───────────────

    /**
     * Project a Nhume delivery-status report onto the selection. Fail-closed
     * cross-check: the reported delivery id must match the stored
     * {@code dispatch_ref}. Terminal facts drive the escrow / refund seams.
     *
     * @return the updated selection, or empty when unknown/mismatched.
     */
    @Transactional
    public Optional<SelectionEntity> onDeliveryStatus(String selectionId, String status,
                                                      String deliveryId, String proofRef,
                                                      String verificationGrade,
                                                      HttpServletRequest inbound) {
        Optional<SelectionEntity> found = selectionRepository.findById(selectionId);
        if (found.isEmpty()) {
            log.warn("Delivery status {} for unknown selection {}", status, selectionId);
            return Optional.empty();
        }
        SelectionEntity selection = found.get();
        if (selection.getDispatchRef() == null || deliveryId == null
                || !selection.getDispatchRef().equals(deliveryId)) {
            // Fail-closed: a status report that does not match the dispatched
            // delivery is never projected (stale or spoofed callback).
            log.warn("Delivery status {} for selection {} REJECTED: delivery id {} does not match dispatch ref {}",
                    status, selectionId, deliveryId, selection.getDispatchRef());
            return Optional.empty();
        }
        String normalised = status != null ? status.trim().toUpperCase(java.util.Locale.ROOT) : "";
        selection.setDeliveryStatus(normalised);
        selection.setDeliveryStatusAt(OffsetDateTime.now());
        switch (normalised) {
            case "DELIVERED" -> {
                selection.setDeliveryProofRef(proofRef);
                selection.setDeliveryVerificationGrade(verificationGrade);
                appendStepLogEntry(selection, "PASS", "FULFILMENT_DELIVERED",
                        null, "PoD-verified DELIVERED (grade "
                                + (verificationGrade != null ? verificationGrade : "UNGRADED")
                                + (proofRef != null ? ", proof " + proofRef : "") + ")");
                handleEscrowSeamOnDelivered(selection);
                publishEvent(selection, "SELECTION_DELIVERED", Map.of(
                        "deliveryId", deliveryId,
                        "proofRef", proofRef != null ? proofRef : "",
                        "verificationGrade", verificationGrade != null ? verificationGrade : "UNGRADED",
                        "escrowReleaseStatus", selection.getEscrowReleaseStatus()));
            }
            case "RETURNED" -> {
                appendStepLogEntry(selection, "FAIL", "FULFILMENT_RETURNED",
                        "DELIVERY_RETURNED", "delivery " + deliveryId
                                + " returned to sender — §12.7 refund path engaged");
                handleRefundOnReturned(selection, inbound);
            }
            default -> publishEvent(selection, "SELECTION_DELIVERY_STATUS", Map.of(
                    "deliveryId", deliveryId, "deliveryStatus", normalised));
        }
        selectionRepository.save(selection);
        return Optional.of(selection);
    }

    /**
     * OF-B18 escrow seam — HONEST DEFERRAL. Selection payments are MusheX
     * intent→PAID with no wallet-escrow hold (§10.7 settled: no two-phase
     * capture; mushe-wallet escrow machinery exists only for crowdfunding
     * campaigns and is not wireable to selection intents without new hold
     * machinery). A PAID selection therefore records ESCROW_RELEASE_PENDING
     * with a coded event as the seam marker for the OF-B10 escrow build;
     * nothing is fabricated as "released".
     */
    private void handleEscrowSeamOnDelivered(SelectionEntity selection) {
        boolean paid = selection.getPaymentIntentId() != null
                && "PAID".equalsIgnoreCase(selection.getPaymentStatus())
                && selection.getShortfallAmount() != null
                && selection.getShortfallAmount().signum() > 0;
        if (!paid) {
            selection.setEscrowReleaseStatus(ESCROW_NOT_APPLICABLE);
            return;
        }
        selection.setEscrowReleaseStatus(ESCROW_RELEASE_PENDING);
        publishEvent(selection, "SELECTION_ESCROW_RELEASE_PENDING", Map.of(
                "paymentIntentId", selection.getPaymentIntentId(),
                "amount", selection.getShortfallAmount().toPlainString(),
                "currency", selection.getShortfallCurrency() != null
                        ? selection.getShortfallCurrency() : "",
                "reason", "no wallet-escrow hold exists for selection payments (intent->PAID, "
                        + "§10.7 no-two-phase-capture); release awaits the OF-B10 escrow build"));
    }

    /** §12.7 refund-on-RETURNED via the existing MusheX refund path — loud either way. */
    private void handleRefundOnReturned(SelectionEntity selection, HttpServletRequest inbound) {
        boolean paid = selection.getPaymentIntentId() != null
                && "PAID".equalsIgnoreCase(selection.getPaymentStatus())
                && selection.getShortfallAmount() != null
                && selection.getShortfallAmount().signum() > 0;
        if (!paid) {
            publishEvent(selection, "SELECTION_RETURNED_NO_REFUND_DUE", Map.of(
                    "detail", "nothing was charged for this selection — no refund due"));
            return;
        }
        Optional<MushexClient.MushexRefundCreated> refund = mushexClient.tryRefund(inbound,
                selection.getPaymentIntentId(), selection.getShortfallAmount(),
                "DELIVERY_RETURNED:" + selection.getSelectionId());
        if (refund.isPresent()) {
            selection.setRefundRef(refund.get().refundId());
            publishEvent(selection, "SELECTION_RETURN_REFUND_REQUESTED", Map.of(
                    "refundId", refund.get().refundId(),
                    "refundStatus", refund.get().status(),
                    "amount", selection.getShortfallAmount().toPlainString()));
        } else {
            // Loud unresolved-money state: the refund transport failed; ops must
            // reconcile — the RETURNED fact itself is already projected.
            publishEvent(selection, "SELECTION_RETURN_REFUND_FAILED", Map.of(
                    "paymentIntentId", selection.getPaymentIntentId(),
                    "amount", selection.getShortfallAmount().toPlainString(),
                    "detail", "MusheX refund request failed — manual reconciliation required"));
        }
    }

    // ── plumbing ─────────────────────────────────────────────────────────

    private DeliveryDetails parseDeliveryDetails(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String name = node.path("name").asText(null);
            String phone = node.path("phone").asText(null);
            String address = node.path("address").asText(null);
            if (isBlank(name) || isBlank(phone) || isBlank(address)) {
                return null;
            }
            Double lat = node.hasNonNull("lat") ? node.get("lat").asDouble() : null;
            Double lng = node.hasNonNull("lng") ? node.get("lng").asDouble() : null;
            return new DeliveryDetails(name, phone, address, lat, lng);
        } catch (Exception e) {
            log.warn("Unparseable delivery details: {}", e.getMessage());
            return null;
        }
    }

    private record DeliveryDetails(String name, String phone, String address, Double lat, Double lng) {}

    /**
     * Append one entry to the selection's §8.9 step log without disturbing the
     * existing history (the log is append-only JSON).
     */
    private void appendStepLogEntry(SelectionEntity selection, String status, String step,
                                    String code, String detail) {
        try {
            com.fasterxml.jackson.databind.node.ArrayNode arr;
            JsonNode existing = selection.getStepLogJson() != null && !selection.getStepLogJson().isBlank()
                    ? objectMapper.readTree(selection.getStepLogJson()) : null;
            arr = existing instanceof com.fasterxml.jackson.databind.node.ArrayNode a
                    ? a : objectMapper.createArrayNode();
            com.fasterxml.jackson.databind.node.ObjectNode n = arr.addObject();
            n.put("seq", 13);
            n.put("step", step);
            n.put("status", status);
            if (code != null) n.put("code", code);
            if (detail != null) n.put("detail", detail);
            n.put("at", OffsetDateTime.now().toString());
            selection.setStepLogJson(arr.toString());
        } catch (Exception e) {
            log.warn("Could not append step-log entry {} to selection {}: {}",
                    step, selection.getSelectionId(), e.getMessage());
        }
    }

    private void publishEvent(SelectionEntity selection, String eventType, Map<String, ?> extra) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("selectionId", selection.getSelectionId());
            payload.put("requestId", selection.getRequestId());
            payload.put("offerId", selection.getOfferId());
            payload.put("tenantId", selection.getTenantId() != null
                    ? selection.getTenantId().toString() : null);
            payload.put("status", selection.getStatus() != null ? selection.getStatus().name() : null);
            payload.put("dispatchRef", selection.getDispatchRef());
            payload.put("dispatchStatus", selection.getDispatchStatus());
            payload.put("deliveryStatus", selection.getDeliveryStatus());
            payload.putAll(extra);
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("MarketplaceSelection");
            outbox.setAggregateId(selection.getSelectionId());
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(selection.getTenantId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write selection outbox event {}: {}", eventType, e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
