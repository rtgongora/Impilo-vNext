package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Nhume is dispatch SoR for marketplace human/community-delivery orders.
 *
 * <p>{@code FulfillmentService.routeOrder} calls this — behind
 * {@code msika-flow.fulfillment.nhume-enabled} — after a delivery plan is created for
 * a non-pickup delivery mode. Failure is swallowed to a warning: the manual
 * out-for-delivery / mark-delivered endpoints remain the documented recovery path, so
 * a Nhume outage never blocks the order from progressing.</p>
 */
@Service
public class NhumeClient {

    private static final Logger log = LoggerFactory.getLogger(NhumeClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public record NhumeDeliveryCreated(String deliveryId, String status) {}

    public NhumeClient(ObjectMapper objectMapper,
                       @Value("${msika-flow.integration.nhume-url:http://nhume-service:8210}") String nhumeBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(nhumeBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * POST /internal/v1/nhume/deliveries (the V11HeaderFilter-covered path; /api/v1 has no RequestContext). Never throws — a Nhume-side failure is logged and
     * treated as "no dispatch id yet", not an order-blocking error.
     */
    public Optional<NhumeDeliveryCreated> createMarketplaceDelivery(OrderEntity order,
                                                                     String mushexPaymentIntentId,
                                                                     HttpServletRequest inbound) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("delivery_type", "MARKETPLACE");
            body.put("request_source", "MSIKA_FLOW");
            body.put("requesting_actor_id", order.getActorId());
            body.put("requesting_actor_type", order.getActorType() != null ? order.getActorType().name() : null);
            if (order.getFacilityId() != null) {
                body.put("requesting_facility_id", order.getFacilityId().toString());
            }
            body.put("marketplace_order_ref", order.getOrderId());

            Map<String, Object> origin = new LinkedHashMap<>();
            if (order.getVendorId() != null) {
                origin.put("kind", "VENDOR");
                origin.put("ref", order.getVendorId().toString());
            } else if (order.getFacilityId() != null) {
                origin.put("kind", "FACILITY");
                origin.put("ref", order.getFacilityId().toString());
            }
            body.put("origin", origin);

            Map<String, Object> destination = new LinkedHashMap<>();
            destination.put("kind", "PATIENT");
            destination.put("ref", order.getPatientCpid());
            body.put("destination", destination);

            Map<String, Object> recipient = new LinkedHashMap<>();
            recipient.put("kind", "PATIENT");
            recipient.put("ref", order.getPatientCpid());
            body.put("recipient", recipient);

            if (mushexPaymentIntentId != null) {
                body.put("payment_path", "PREPAID");
                body.put("payment_reference", mushexPaymentIntentId);
            }
            body.put("submit_immediately", true);

            Map<String, Object> links = new LinkedHashMap<>();
            links.put("msikaFlowOrderRef", order.getOrderId());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("links", links);
            body.put("metadata", metadata);

            ResponseEntity<String> response = restClient.post()
                    .uri("/internal/v1/nhume/deliveries")
                    .headers(h -> copyTrustHeaders(inbound, h, order.getOrderId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Nhume delivery create failed for order {}: HTTP {}", order.getOrderId(), response.getStatusCode());
                return Optional.empty();
            }
            // Nhume's DeliveryResponse is the bare object (snake_case, no {data} wrapper);
            // tolerate a wrapped form for forward-compat.
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.hasNonNull("data") && root.get("data").isObject() ? root.get("data") : root;
            String deliveryId = data.hasNonNull("delivery_id") ? data.get("delivery_id").asText()
                    : data.hasNonNull("deliveryId") ? data.get("deliveryId").asText() : null;
            if (deliveryId == null || deliveryId.isBlank()) {
                log.warn("Nhume delivery create response missing delivery_id for order {}", order.getOrderId());
                return Optional.empty();
            }
            String status = data.hasNonNull("status") ? data.get("status").asText() : "CREATED";
            return Optional.of(new NhumeDeliveryCreated(deliveryId, status));
        } catch (Exception e) {
            log.warn("Nhume delivery create failed for order {}: {}", order.getOrderId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * OF-B17 — the courier-minimum delivery-task spec built from a COMMITTED
     * marketplace selection (§12.1/§12.2). Cargo lines are OPAQUE refs (offer
     * line ids) — item codes, medication names and any clinical content are
     * FORBIDDEN on the courier surface (CC-9); handling flags ride separately.
     */
    public record CommitmentCargoLine(String offerLineRef, int quantity,
                                      boolean controlled, boolean coldChain) {}

    public record CommitmentDeliverySpec(
            String selectionId,
            String requestId,
            java.util.UUID tenantId,
            String vendorId,
            String orosOrderRef,
            String dispenseEpisodeRef,     // OROS prescription-claim id where present
            boolean controlled,
            boolean coldChain,
            String recipientName,          // §11.8 delivery-minimum block
            String recipientPhone,
            String deliveryAddress,
            Double deliveryLat,
            Double deliveryLng,
            String requiredPodGrade,       // §12.4 grade demand
            String paymentReference,       // MusheX intent id when paid
            java.util.List<CommitmentCargoLine> cargoLines) {}

    /**
     * OF-B17 — create the Nhume delivery task for a COMMITTED selection
     * (§12.1). Idempotency-Key is stable on the selection id (RC-8): an
     * at-least-once dispatch can never create two deliveries. Never throws —
     * a Nhume-side failure returns empty and the caller records the honest
     * {@code DISPATCH_FAILED} outcome (the commitment stands; retry sweep
     * re-attempts).
     */
    public Optional<NhumeDeliveryCreated> createCommitmentDelivery(CommitmentDeliverySpec spec,
                                                                   HttpServletRequest inbound) {
        try {
            Map<String, Object> body = commitmentDeliveryBody(spec);
            ResponseEntity<String> response = restClient.post()
                    .uri("/internal/v1/nhume/deliveries")
                    .headers(h -> {
                        copyTrustHeaders(inbound, h, "selection:" + spec.selectionId());
                        // RC-8: override with the selection-stable key.
                        h.set("Idempotency-Key", "msika-flow-selection-dispatch:" + spec.selectionId());
                        h.set("X-Idempotency-Key", "msika-flow-selection-dispatch:" + spec.selectionId());
                        if (inbound == null) {
                            applyServiceOriginatedHeaders(h, spec.tenantId());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Nhume commitment delivery create failed for selection {}: HTTP {}",
                        spec.selectionId(), response.getStatusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.hasNonNull("data") && root.get("data").isObject() ? root.get("data") : root;
            String deliveryId = data.hasNonNull("delivery_id") ? data.get("delivery_id").asText()
                    : data.hasNonNull("deliveryId") ? data.get("deliveryId").asText() : null;
            if (deliveryId == null || deliveryId.isBlank()) {
                log.warn("Nhume commitment delivery response missing delivery_id for selection {}",
                        spec.selectionId());
                return Optional.empty();
            }
            String status = data.hasNonNull("status") ? data.get("status").asText() : "CREATED";
            return Optional.of(new NhumeDeliveryCreated(deliveryId, status));
        } catch (Exception e) {
            log.warn("Nhume commitment delivery create failed for selection {}: {}",
                    spec.selectionId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * §12.2 courier-minimum body (exposed for the PII contract test):
     * opaque cargo refs, handling flags, delivery-minimum recipient block —
     * NEVER item codes, medication names, diagnosis, coverage or prescriber
     * identity.
     */
    public static Map<String, Object> commitmentDeliveryBody(CommitmentDeliverySpec spec) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delivery_type", "MARKETPLACE");
        body.put("request_source", "MARKETPLACE_COMMITMENT");
        body.put("requesting_actor_id", "msika-flow-service");
        body.put("requesting_actor_type", "SYSTEM");

        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("kind", "VENDOR");
        origin.put("ref", spec.vendorId());
        body.put("origin", origin);

        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("kind", "ADDRESS");
        destination.put("ref", "selection:" + spec.selectionId());
        destination.put("address", spec.deliveryAddress());
        if (spec.deliveryLat() != null) destination.put("lat", spec.deliveryLat());
        if (spec.deliveryLng() != null) destination.put("lng", spec.deliveryLng());
        body.put("destination", destination);

        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("kind", "PATIENT");
        recipient.put("ref", "selection:" + spec.selectionId()); // opaque — never raw CPID/HID (§11.8)
        recipient.put("name", spec.recipientName());
        recipient.put("phone", spec.recipientPhone());
        body.put("recipient", recipient);

        // §12.2/CC-9 — opaque cargo descriptors: offer-line refs + handling flags only.
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (CommitmentCargoLine line : spec.cargoLines()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("item_kind", "MARKETPLACE_LINE");
            item.put("item_ref", line.offerLineRef());
            item.put("description", "Sealed marketplace package line");
            item.put("quantity", line.quantity());
            item.put("controlled", line.controlled());
            item.put("cold_chain", line.coldChain());
            items.add(item);
        }
        body.put("items", items);

        body.put("controlled_item", spec.controlled());
        body.put("cold_chain_required", spec.coldChain());
        body.put("chain_of_custody_required", true);
        // OF-B18 §12.4 — named-recipient enforcement + per-class grade demand.
        body.put("named_recipient_required", true);
        body.put("required_pod_grade", spec.requiredPodGrade());
        if (spec.paymentReference() != null) {
            body.put("payment_path", "PREPAID");
            body.put("payment_reference", spec.paymentReference());
        }
        body.put("submit_immediately", true);

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("msikaFlowSelectionRef", spec.selectionId());
        links.put("msikaFlowRequestRef", spec.requestId());
        if (spec.orosOrderRef() != null) {
            links.put("orosOrderRef", spec.orosOrderRef());
        }
        if (spec.dispenseEpisodeRef() != null) {
            links.put("dispenseEpisodeRef", spec.dispenseEpisodeRef());
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("links", links);
        body.put("metadata", metadata);
        return body;
    }

    /**
     * M3 BUG-10 — the FULL v1.1 hard-required set for service-originated
     * (inbound == null) calls: payment-resumed commitments and the
     * {@code FulfilmentDispatchService.retryFailedDispatches} sweep run without
     * a servlet request. The previous block omitted {@code X-Correlation-ID},
     * so Nhume's V11HeaderFilter 400'd (MISSING_REQUIRED_HEADER) every
     * payment-resumed / retried dispatch — DELIVERY commitments that went
     * AWAITING_PAYMENT→PAID were permanently undeliverable. Package-visible for
     * the header-contract unit test.
     */
    static void applyServiceOriginatedHeaders(HttpHeaders h, java.util.UUID tenantId) {
        h.set("X-Tenant-ID", tenantId.toString());
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", java.util.UUID.randomUUID().toString());
        h.set("X-Correlation-ID", java.util.UUID.randomUUID().toString());
        h.set("X-Actor-ID", "msika-flow-service");
        h.set("X-Actor-Type", "SYSTEM");
        h.set("X-Purpose-Of-Use", "LOGISTICS");
        h.set("x-envoy-internal", "true");
    }

    /**
     * MushexClient {@code copyTrustHeaders} idiom, reused for the Nhume hop — plus the
     * v1.1 hard-required set Nhume's tech-companion V11HeaderFilter enforces
     * (MISSING_REQUIRED_HEADER defect family): X-Pod-ID forwarded or defaulted,
     * X-Request-ID synthesized per call, Idempotency-Key stable per order so a
     * retried route never double-creates the delivery.
     */
    private static void copyTrustHeaders(HttpServletRequest inbound, HttpHeaders target, String orderId) {
        if (inbound == null) {
            return;
        }
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_TENANT_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_TYPE);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_PURPOSE_OF_USE);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_DEVICE_FINGERPRINT);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_CORRELATION_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_FACILITY_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_WORKSPACE_ID);
        copyIfPresent(inbound, target, TrustHeaderExtractor.H_SHIFT_ID);
        copyIfPresent(inbound, target, "Authorization");
        String pod = inbound.getHeader("X-Pod-ID");
        target.add("X-Pod-ID", pod != null && !pod.isBlank() ? pod : "national-spine");
        target.add("X-Request-ID", java.util.UUID.randomUUID().toString());
        String idempotencyKey = "msika-flow-dispatch:" + orderId;
        target.add("Idempotency-Key", idempotencyKey);
        target.add("X-Idempotency-Key", idempotencyKey);
        target.add("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound, HttpHeaders target, String name) {
        String v = inbound.getHeader(name);
        if (v != null && !v.isBlank()) {
            target.add(name, v);
        }
    }
}
