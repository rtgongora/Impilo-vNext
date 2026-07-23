package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderVersionEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderVersionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Creates immutable order-version snapshots (OF-B1).
 *
 * <p>The canonical serialisation is a sorted-key JSON object over the clinically
 * meaningful order fields plus the item list — deterministic, so the SHA-256
 * {@code contentHash} is stable for identical content and the OF-B2 detached JWS
 * has a well-defined signing input.</p>
 *
 * <p>Deliberately a lean component (repositories + mapper only) so both
 * {@link OrderStateMachine} and {@link OrderLifecycleService} can use it
 * without a dependency cycle.</p>
 */
@Component
public class OrderVersionWriter {

    private static final Logger log = LoggerFactory.getLogger(OrderVersionWriter.class);

    private final OrderVersionRepository versionRepository;
    private final OrderItemRepository orderItemRepository;
    private final ObjectMapper objectMapper;

    public OrderVersionWriter(OrderVersionRepository versionRepository,
                              OrderItemRepository orderItemRepository,
                              ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.orderItemRepository = orderItemRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Snapshot the order's current content as the next version. Returns the new head.
     *
     * <p>Race guard: {@code UNIQUE(order_id, version)} — two concurrent snapshots
     * compute the same next version and exactly one insert wins; the loser surfaces
     * as {@code AMEND_CONFLICT} (409) for the caller to retry against the new head.</p>
     */
    public OrderVersionEntity snapshot(OrderEntity order, String reason, String actorId) {
        OrderVersionEntity head = versionRepository
                .findFirstByOrderIdOrderByVersionDesc(order.getOrderId()).orElse(null);

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getOrderId());
        String canonical = canonicalContent(order, items);

        OrderVersionEntity next = new OrderVersionEntity();
        next.setOrderVersionId(OrderStateMachine.generateUlid());
        next.setOrderId(order.getOrderId());
        next.setTenantId(order.getTenantId());
        next.setVersion(head != null ? head.getVersion() + 1 : 1);
        next.setSupersedesVersionId(head != null ? head.getOrderVersionId() : null);
        next.setContentJson(canonical);
        next.setContentHash(sha256Hex(canonical));
        next.setReason(reason);
        next.setCreatedBy(actorId);

        try {
            OrderVersionEntity saved = versionRepository.save(next);
            versionRepository.flush();
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Concurrent snapshot won the (order_id, version) race.
            throw new OrosDomainException("AMEND_CONFLICT", 409,
                    "Order " + order.getOrderId() + " was amended concurrently — reload and retry.");
        }
    }

    /** True if the order already has at least one version row. */
    public boolean hasVersions(String orderId) {
        return versionRepository.existsByOrderId(orderId);
    }

    /**
     * Canonical (sorted-key) JSON over the clinically meaningful order content + items.
     * Ordering-stable so the hash is deterministic; workflow/status fields are excluded —
     * a version captures WHAT was ordered, not where fulfilment got to.
     */
    String canonicalContent(OrderEntity order, List<OrderItemEntity> items) {
        Map<String, Object> content = new TreeMap<>();
        content.put("orderId", order.getOrderId());
        content.put("orderType", order.getOrderType() != null ? order.getOrderType().name() : null);
        content.put("patientCpid", order.getPatientCpid());
        content.put("facilityId", order.getFacilityId() != null ? order.getFacilityId().toString() : null);
        content.put("priority", order.getPriority() != null ? order.getPriority().name() : null);
        content.put("ziboOrderCode", order.getZiboOrderCode());
        content.put("encounterRef", order.getEncounterRef());
        content.put("clinicalNotes", order.getClinicalNotes());
        content.put("requestSource", order.getRequestSource() != null ? order.getRequestSource().name() : null);
        content.put("sourceRef", order.getSourceRef());
        content.put("referringProviderId", order.getReferringProviderId());
        content.put("scheduledAt", order.getScheduledAt() != null ? order.getScheduledAt().toString() : null);
        content.put("safetyJson", order.getSafetyJson());

        List<Map<String, Object>> itemList = new ArrayList<>();
        for (OrderItemEntity item : items) {
            Map<String, Object> m = new TreeMap<>();
            m.put("code", item.getCode());
            m.put("displayName", item.getDisplayName());
            m.put("quantity", item.getQuantity());
            m.put("instructions", item.getInstructions());
            m.put("specimenType", item.getSpecimenType());
            m.put("bodySite", item.getBodySite());
            m.put("modality", item.getModality());
            m.put("laterality", item.getLaterality());
            m.put("contrast", item.getContrast());
            m.put("procedureCode", item.getProcedureCode());
            itemList.add(m);
        }
        // Stable item order: by code then display name (item PKs are DB-generated).
        itemList.sort((a, b) -> {
            int c = String.valueOf(a.get("code")).compareTo(String.valueOf(b.get("code")));
            return c != 0 ? c : String.valueOf(a.get("displayName")).compareTo(String.valueOf(b.get("displayName")));
        });
        content.put("items", itemList);

        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            // Canonicalisation failure is a programming error, never a swallowable event.
            throw new IllegalStateException("Failed to canonicalise order content for " + order.getOrderId(), e);
        }
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
