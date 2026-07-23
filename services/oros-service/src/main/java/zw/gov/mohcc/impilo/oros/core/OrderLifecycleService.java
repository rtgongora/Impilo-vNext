package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderVersionEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderVersionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Order lifecycle operations introduced by OF-B1 (Vol II §9.1 T4–T9):
 * amend (new immutable version), hold/resume, revoke, replace, error-mark.
 *
 * <p>Doctrine: amendment is a NEW VERSION, never in-place clinical mutation;
 * reasons are structured ({@code lifecycle_reason} + event payload), never
 * appended into {@code clinical_notes}; every operation is tenant-scoped and
 * emits its lifecycle event through the outbox.</p>
 */
@Service
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    /** States from which content amendment is permitted (pre-fulfilment; §9.1 T7 context). */
    private static final Set<OrderStatus> AMENDABLE = EnumSet.of(
            OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.ON_HOLD);

    /** States a hold can be applied from — T4. */
    private static final Set<OrderStatus> HOLDABLE = EnumSet.of(
            OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS);

    /** Pre-fulfilment states eligible for governance error-marking — T9. */
    private static final Set<OrderStatus> ERROR_MARKABLE = EnumSet.of(
            OrderStatus.DRAFT, OrderStatus.PENDING_SIGNATURE, OrderStatus.PLACED,
            OrderStatus.ACCEPTED, OrderStatus.SCHEDULED);

    private final OrderStateMachine stateMachine;
    private final OrderVersionWriter versionWriter;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public OrderLifecycleService(OrderStateMachine stateMachine,
                                 OrderVersionWriter versionWriter,
                                 OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 OrderVersionRepository versionRepository,
                                 ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.versionWriter = versionWriter;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    /** Content fields an amendment may change. Null members mean "leave unchanged". */
    public record AmendRequest(
            String clinicalNotes,
            String ziboOrderCode,
            String encounterRef,
            String safetyJson,
            List<OrderStateMachine.OrderItemData> items,
            String reason
    ) {}

    /**
     * Amend a placed order: apply the content changes, snapshot them as the next
     * immutable version, emit {@code ORDER_AMENDED} → {@code oros.order.amended.v1}.
     */
    @Transactional
    public OrderVersionEntity amend(String orderId, AmendRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = stateMachine.getOrder(orderId);
        requireReason(request.reason());

        if (!AMENDABLE.contains(order.getStatus())) {
            throw new OrosDomainException("ORDER_NOT_AMENDABLE", 409,
                    "Order " + orderId + " cannot be amended in status " + order.getStatus()
                            + " — amendable states: " + AMENDABLE);
        }

        if (request.clinicalNotes() != null) order.setClinicalNotes(request.clinicalNotes());
        if (request.ziboOrderCode() != null) order.setZiboOrderCode(request.ziboOrderCode());
        if (request.encounterRef() != null) order.setEncounterRef(request.encounterRef());
        if (request.safetyJson() != null) order.setSafetyJson(request.safetyJson());
        order.setLifecycleReason(request.reason());
        order = orderRepository.save(order);

        if (request.items() != null && !request.items().isEmpty()) {
            orderItemRepository.deleteByOrderId(orderId);
            for (OrderStateMachine.OrderItemData d : request.items()) {
                OrderItemEntity item = new OrderItemEntity();
                item.setOrderId(orderId);
                item.setCode(d.code());
                item.setDisplayName(d.displayName());
                item.setQuantity(d.quantity());
                item.setInstructions(d.instructions());
                item.setSpecimenType(d.specimenType());
                item.setBodySite(d.bodySite());
                item.setModality(d.modality());
                item.setLaterality(d.laterality());
                item.setContrast(d.contrast());
                item.setProcedureCode(d.procedureCode());
                orderItemRepository.save(item);
            }
        }

        OrderVersionEntity version = versionWriter.snapshot(order, request.reason(), ctx.actorId());

        stateMachine.publishOrderEvent(orderId, "ORDER_AMENDED",
                amendPayload(order, version), ctx.tenantId());

        log.info("Order amended: orderId={}, version={} supersedes={}",
                orderId, version.getVersion(), version.getSupersedesVersionId());
        return version;
    }

    /** T4: apply a hold with a coded reason, remembering the state to resume to. */
    @Transactional
    public OrderEntity hold(String orderId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = stateMachine.getOrder(orderId);
        requireReason(reason);

        if (!HOLDABLE.contains(order.getStatus())) {
            throw new OrosDomainException("ORDER_NOT_HOLDABLE", 409,
                    "Order " + orderId + " cannot be held in status " + order.getStatus());
        }

        order.setHoldPriorStatus(order.getStatus().name());
        order.setLifecycleReason(reason);
        return stateMachine.transition(order, OrderStatus.ON_HOLD);
    }

    /** T5: release a hold, returning to the state the hold was applied from. */
    @Transactional
    public OrderEntity resume(String orderId) {
        OrderEntity order = stateMachine.getOrder(orderId);

        if (order.getStatus() != OrderStatus.ON_HOLD) {
            throw new OrosDomainException("ORDER_NOT_ON_HOLD", 409,
                    "Order " + orderId + " is not on hold (status=" + order.getStatus() + ")");
        }
        OrderStatus prior;
        try {
            prior = OrderStatus.valueOf(order.getHoldPriorStatus());
        } catch (Exception e) {
            throw new OrosDomainException("HOLD_STATE_CORRUPT", 500,
                    "Order " + orderId + " has no recoverable pre-hold status.");
        }
        order.setHoldPriorStatus(null);
        order.setLifecycleReason(null);
        return stateMachine.transition(order, prior);
    }

    /** T6: clinical revocation by prescriber/governance — terminal. OF-B2 wires token revocation here. */
    @Transactional
    public OrderEntity revoke(String orderId, String reason) {
        OrderEntity order = stateMachine.getOrder(orderId);
        requireReason(reason);
        order.setLifecycleReason(reason);
        return stateMachine.transition(order, OrderStatus.REVOKED);
    }

    /**
     * T7: replace an order with a successor. The successor is a new DRAFT order carrying
     * the predecessor's head content; the predecessor becomes terminal {@code REPLACED}.
     * Both sides carry the link in {@code external_refs}; both events are emitted.
     */
    @Transactional
    public OrderEntity replace(String orderId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity predecessor = stateMachine.getOrder(orderId);
        requireReason(reason);

        if (predecessor.getStatus() != OrderStatus.PLACED) {
            throw new OrosDomainException("ORDER_NOT_REPLACEABLE", 409,
                    "Order " + orderId + " cannot be replaced in status " + predecessor.getStatus()
                            + " — only PLACED orders are replaceable (T7).");
        }

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        List<OrderStateMachine.OrderItemData> itemData = items.stream()
                .map(i -> new OrderStateMachine.OrderItemData(
                        i.getCode(), i.getDisplayName(), i.getQuantity(), i.getInstructions(),
                        i.getSpecimenType(), i.getBodySite(), i.getModality(), i.getLaterality(),
                        i.getContrast(), i.getProcedureCode()))
                .toList();

        OrderEntity successor = stateMachine.createDraft(
                predecessor.getFacilityId(), predecessor.getPatientCpid(), predecessor.getOrderType(),
                predecessor.getPriority(), predecessor.getRequestSource(), predecessor.getSourceRef(),
                predecessor.getZiboOrderCode(), predecessor.getEncounterRef(), predecessor.getClinicalNotes(),
                predecessor.getReferringProviderId(), predecessor.getReferringProviderName(),
                predecessor.getScheduledAt(), predecessor.getSafetyJson(), itemData);

        successor.setExternalRefs(mergeRef(successor.getExternalRefs(), "replaces", orderId));
        orderRepository.save(successor);

        predecessor.setExternalRefs(mergeRef(predecessor.getExternalRefs(), "replacedBy", successor.getOrderId()));
        predecessor.setLifecycleReason(reason);
        stateMachine.transition(predecessor, OrderStatus.REPLACED);

        log.info("Order replaced: predecessor={} successor={} reason={}",
                orderId, successor.getOrderId(), reason);
        return successor;
    }

    /** T9: governance error-marking — terminal, pre-fulfilment only, hidden from clinical reads. */
    @Transactional
    public OrderEntity markEnteredInError(String orderId, String reason) {
        OrderEntity order = stateMachine.getOrder(orderId);
        requireReason(reason);

        if (!ERROR_MARKABLE.contains(order.getStatus())) {
            throw new OrosDomainException("ORDER_NOT_ERROR_MARKABLE", 409,
                    "Order " + orderId + " cannot be error-marked in status " + order.getStatus()
                            + " — fulfilment has progressed; use correction flows, not erasure.");
        }
        order.setLifecycleReason(reason);
        return stateMachine.transition(order, OrderStatus.ENTERED_IN_ERROR);
    }

    /**
     * M3 BUG-2 — the OF-B12 claim-carriage producer lane. Keys writable through
     * the narrow {@code POST /v1/orders/{orderId}/external-refs} endpoint;
     * everything else is refused (external_refs also carries the T7
     * replaces/replacedBy links, which must never be caller-writable).
     */
    private static final Set<String> EXTERNAL_REF_ALLOWLIST = Set.of("prescriptionClaimId");

    /**
     * M3 BUG-2 — record one allowlisted external reference on an order
     * (merge-into-jsonb). Idempotent: re-recording the same key=value is a
     * no-op; a DIFFERENT value for an already-recorded key is a 409 conflict
     * (a claim id never legitimately changes). msika-flow's CommitmentService
     * calls this after the successful step-6 prescription claim so the
     * {@code oros.order.placed}→pharmacy consumer flow (and the pharmacy lazy
     * re-fetch) can bind the claim onto the dispense episode.
     */
    @Transactional
    public OrderEntity recordExternalRef(String orderId, String key, String value) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = stateMachine.getOrder(orderId);
        if (key == null || !EXTERNAL_REF_ALLOWLIST.contains(key)) {
            throw new OrosDomainException("EXTERNAL_REF_KEY_NOT_ALLOWED", 400,
                    "External-ref key not allowed — writable keys: " + EXTERNAL_REF_ALLOWLIST);
        }
        if (value == null || value.isBlank()) {
            throw new OrosDomainException("EXTERNAL_REF_VALUE_REQUIRED", 400,
                    "External-ref value is required.");
        }
        String existing = readRef(order.getExternalRefs(), key);
        if (value.equals(existing)) {
            return order; // idempotent replay — nothing to do
        }
        if (existing != null) {
            throw new OrosDomainException("EXTERNAL_REF_CONFLICT", 409,
                    "Order " + orderId + " already carries " + key
                            + " with a different value — refusing silent overwrite.");
        }
        order.setExternalRefs(mergeRef(order.getExternalRefs(), key, value));
        order = orderRepository.save(order);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId);
        payload.put("key", key);
        payload.put("value", value);
        stateMachine.publishOrderEvent(orderId, "ORDER_EXTERNAL_REF_RECORDED", payload, ctx.tenantId());

        log.info("Order external ref recorded: orderId={} key={}", orderId, key);
        return order;
    }

    /** Parsed external_refs document for an order (empty map when none). Tenant-scoped. */
    public Map<String, String> externalRefs(String orderId) {
        OrderEntity order = stateMachine.getOrder(orderId);
        Map<String, String> out = new LinkedHashMap<>();
        String json = order.getExternalRefs();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            var node = objectMapper.readTree(json);
            node.fieldNames().forEachRemaining(name -> {
                var v = node.get(name);
                if (v != null && v.isValueNode()) {
                    out.put(name, v.asText());
                }
            });
        } catch (Exception e) {
            log.warn("Unparseable external_refs on order {}: {}", orderId, e.getMessage());
        }
        return out;
    }

    private String readRef(String externalRefsJson, String key) {
        if (externalRefsJson == null || externalRefsJson.isBlank()) {
            return null;
        }
        try {
            var v = objectMapper.readTree(externalRefsJson).get(key);
            return v != null && !v.isNull() ? v.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Version history for an order, newest first, tenant-scoped. */
    public List<OrderVersionEntity> versions(String orderId) {
        stateMachine.getOrder(orderId); // tenant-scoped IDOR guard
        UUID tenantId = TrustContextHolder.require().tenantId();
        return versionRepository.findByTenantIdAndOrderIdOrderByVersionDesc(tenantId, orderId);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new OrosDomainException("REASON_REQUIRED", 400,
                    "A reason is required for this lifecycle action.");
        }
    }

    private Object amendPayload(OrderEntity order, OrderVersionEntity version) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("orderId", order.getOrderId());
        node.put("orderVersionId", version.getOrderVersionId());
        node.put("version", version.getVersion());
        node.put("supersedesVersionId", version.getSupersedesVersionId());
        node.put("contentHash", version.getContentHash());
        node.put("reason", version.getReason());
        node.put("status", order.getStatus().name());
        return node;
    }

    private String mergeRef(String externalRefsJson, String key, String value) {
        try {
            ObjectNode node = externalRefsJson == null || externalRefsJson.isBlank()
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.readTree(externalRefsJson);
            node.put(key, value);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Could not merge external ref {}={}: {}", key, value, e.getMessage());
            return externalRefsJson;
        }
    }
}
