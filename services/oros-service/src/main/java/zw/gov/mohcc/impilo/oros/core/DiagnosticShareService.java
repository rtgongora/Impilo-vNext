package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.integration.ShareSlipClient;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;

/**
 * Patient-carried QR for diagnostic orders (acceptance criterion D): issue a printable secure
 * order link (document-less, via share-slip {@code subjectType=DIAGNOSTIC_ORDER}) and claim it at
 * a standalone/external facility.
 *
 * <p>Claim is OTP-protected at share-slip and additionally requires an authenticated OROS caller
 * (Envoy/TSHEPO edge), so no sensitive order data is exposed without auth + a valid OTP.</p>
 */
@Service
public class DiagnosticShareService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticShareService.class);
    private static final String SUBJECT_TYPE = "DIAGNOSTIC_ORDER";

    private final OrderStateMachine stateMachine;
    private final ShareSlipClient shareSlipClient;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DiagnosticShareService(OrderStateMachine stateMachine,
                                  ShareSlipClient shareSlipClient,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.shareSlipClient = shareSlipClient;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** Issue a printable, OTP-protected QR for the order (document-less DIAGNOSTIC_ORDER link). */
    @Transactional
    public ShareSlipClient.ShareLinkRef printable(String orderId, int expiryHours, int maxClaims) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = stateMachine.getOrder(orderId);
        String name = "Diagnostic order " + (order.getAccessionNumber() != null
                ? order.getAccessionNumber() : order.getOrderId());

        ShareSlipClient.ShareLinkRef ref = shareSlipClient.createLink(
                SUBJECT_TYPE, orderId, name, List.of(), "QR order intake", expiryHours, maxClaims);

        publish(orderId, "ORDER_PRINTABLE_ISSUED",
                Map.of("orderId", orderId, "linkId", ref.linkId() != null ? ref.linkId() : ""),
                ctx.tenantId());
        log.info("Issued printable QR for order={}, linkId={}", orderId, ref.linkId());
        return ref;
    }

    /**
     * Claim a patient-carried order QR. Validates the OTP via share-slip, confirms the claimed
     * subject is a diagnostic order, and returns it.
     *
     * @throws IllegalStateException if the claim fails or the token is not a diagnostic order
     */
    @Transactional
    public OrderEntity claim(String shareToken, String otp, String deviceFingerprint) {
        TrustContext ctx = TrustContextHolder.require();
        ShareSlipClient.ClaimRef claim = shareSlipClient.claim(shareToken, otp, deviceFingerprint);
        if (!claim.success()) {
            throw new IllegalStateException("QR claim failed: "
                    + (claim.message() != null ? claim.message() : "invalid token or OTP"));
        }
        if (!SUBJECT_TYPE.equals(claim.subjectType()) || claim.subjectId() == null) {
            throw new IllegalStateException("QR does not reference a diagnostic order");
        }

        OrderEntity order = stateMachine.getOrder(claim.subjectId());
        publish(order.getOrderId(), "ORDER_QR_CLAIMED",
                Map.of("orderId", order.getOrderId(), "claimedBy", ctx.actorId()),
                ctx.tenantId());
        log.info("Order QR claimed: orderId={}, by={}", order.getOrderId(), ctx.actorId());
        return order;
    }

    private void publish(String orderId, String eventType, Map<String, Object> payload, java.util.UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("ORDER");
            event.setAggregateId(orderId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write {} event for order {}: {}", eventType, orderId, e.getMessage(), e);
        }
    }
}
