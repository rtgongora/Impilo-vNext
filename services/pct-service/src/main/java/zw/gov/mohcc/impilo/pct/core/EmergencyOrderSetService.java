package zw.gov.mohcc.impilo.pct.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyOrderSetInstanceEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyOrderSetItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyOrderSetInstanceRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyOrderSetItemRepository;

/**
 * Order-set instance tracking (pct V206, W7b): what actually happened when a clinician invoked a
 * CKP-defined bundle (step_type=ORDER_SET) for a specific emergency episode.
 *
 * <p>CKP holds the bundle's DEFINITION; this service never calls CKP to re-derive it. The caller
 * (BFF/UI, which already read the pathway step's {@code data_capture_schema}) supplies the concrete
 * {@code order_set_code}/{@code order_set_name} and item list in the invocation request. This mirrors
 * how the rest of this pack keeps content-serving and transactional-write paths on different sides of
 * the client boundary — a synchronous pct-to-ckp call inside a write path is exactly the
 * cross-service coupling this estate has learned to avoid.
 */
@Service
public class EmergencyOrderSetService {

    private final EmergencyEpisodeRepository episodeRepository;
    private final EmergencyOrderSetInstanceRepository instanceRepository;
    private final EmergencyOrderSetItemRepository itemRepository;

    public EmergencyOrderSetService(EmergencyEpisodeRepository episodeRepository,
                                    EmergencyOrderSetInstanceRepository instanceRepository,
                                    EmergencyOrderSetItemRepository itemRepository) {
        this.episodeRepository = episodeRepository;
        this.instanceRepository = instanceRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * Invoke a bundle: create the instance and one PENDING item per entry in {@code items}. Refuses
     * an episode that does not exist in this tenant (the same guard every other emergency write in
     * this pack applies before touching a row).
     */
    @Transactional
    public EmergencyOrderSetInstanceEntity invoke(UUID episodeId, UUID tenantId, Map<String, Object> body) {
        episodeRepository.findByEpisodeIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency episode not found"));

        String orderSetCode = str(body, "orderSetCode", "order_set_code");
        if (orderSetCode == null || orderSetCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order_set_code is required");
        }
        String invokedBy = str(body, "invokedBy", "invoked_by");
        if (invokedBy == null || invokedBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invoked_by is required");
        }

        EmergencyOrderSetInstanceEntity instance = new EmergencyOrderSetInstanceEntity();
        instance.setTenantId(tenantId);
        instance.setEpisodeId(episodeId);
        instance.setVisitId(uuidVal(body, "visitId", "visit_id"));
        instance.setPathwaySessionId(uuidVal(body, "pathwaySessionId", "pathway_session_id"));
        instance.setOrderSetCode(orderSetCode);
        instance.setOrderSetName(str(body, "orderSetName", "order_set_name"));
        instance.setInvokedBy(invokedBy);
        instance = instanceRepository.save(instance);

        Object itemsRaw = body.get("items");
        if (itemsRaw instanceof List<?> items) {
            for (Object o : items) {
                if (!(o instanceof Map<?, ?> itemMap)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> im = (Map<String, Object>) itemMap;
                EmergencyOrderSetItemEntity item = new EmergencyOrderSetItemEntity();
                item.setTenantId(tenantId);
                item.setInstanceId(instance.getInstanceId());
                item.setOrderCode(str(im, "orderCode", "order_code"));
                item.setOrderLabel(str(im, "orderLabel", "order_label"));
                item.setOrderType(str(im, "orderType", "order_type"));
                itemRepository.save(item);
            }
        }
        return instance;
    }

    @Transactional(readOnly = true)
    public EmergencyOrderSetInstanceEntity getInstance(UUID instanceId, UUID tenantId) {
        return loadInstance(instanceId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<EmergencyOrderSetItemEntity> items(UUID instanceId, UUID tenantId) {
        loadInstance(instanceId, tenantId); // tenant-scoping check
        return itemRepository.findByInstanceIdOrderByCreatedAtAsc(instanceId);
    }

    @Transactional(readOnly = true)
    public List<EmergencyOrderSetInstanceEntity> forEpisode(UUID episodeId, UUID tenantId) {
        return instanceRepository.findByTenantIdAndEpisodeIdOrderByInvokedAtDesc(tenantId, episodeId);
    }

    /** Mark an item ORDERED. Optionally correlates the pct.ed_diagnostic_order it produced. */
    @Transactional
    public EmergencyOrderSetItemEntity orderItem(UUID itemId, UUID tenantId, Map<String, Object> body) {
        EmergencyOrderSetItemEntity item = loadItem(itemId, tenantId);
        String orderedBy = str(body, "orderedBy", "ordered_by");
        if (orderedBy == null || orderedBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ordered_by is required");
        }
        item.setStatus("ORDERED");
        item.setOrderedAt(OffsetDateTime.now());
        item.setOrderedBy(orderedBy);
        item.setDiagnosticOrderId(uuidVal(body, "diagnosticOrderId", "diagnostic_order_id"));
        item = itemRepository.save(item);
        completeInstanceIfAllResolved(item.getInstanceId(), tenantId);
        return item;
    }

    /** Decline an item. Refuses a blank reason at the application layer too — the DB CHECK is the real guarantee. */
    @Transactional
    public EmergencyOrderSetItemEntity declineItem(UUID itemId, UUID tenantId, Map<String, Object> body) {
        EmergencyOrderSetItemEntity item = loadItem(itemId, tenantId);
        String declinedBy = str(body, "declinedBy", "declined_by");
        String reason = str(body, "reason", "declineReason", "decline_reason");
        if (declinedBy == null || declinedBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "declined_by is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decline_reason is required");
        }
        item.setStatus("DECLINED");
        item.setDeclinedAt(OffsetDateTime.now());
        item.setDeclinedBy(declinedBy);
        item.setDeclineReason(reason);
        item = itemRepository.save(item);
        completeInstanceIfAllResolved(item.getInstanceId(), tenantId);
        return item;
    }

    /** Once every item has left PENDING, the instance is COMPLETED — derived, never set directly by a caller. */
    private void completeInstanceIfAllResolved(UUID instanceId, UUID tenantId) {
        List<EmergencyOrderSetItemEntity> siblings = itemRepository.findByInstanceIdOrderByCreatedAtAsc(instanceId);
        boolean allResolved = !siblings.isEmpty()
                && siblings.stream().noneMatch(i -> "PENDING".equals(i.getStatus()));
        if (allResolved) {
            instanceRepository.findByInstanceIdAndTenantId(instanceId, tenantId).ifPresent(instance -> {
                instance.setStatus("COMPLETED");
                instanceRepository.save(instance);
            });
        }
    }

    private EmergencyOrderSetInstanceEntity loadInstance(UUID instanceId, UUID tenantId) {
        return instanceRepository.findByInstanceIdAndTenantId(instanceId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order-set instance not found"));
    }

    private EmergencyOrderSetItemEntity loadItem(UUID itemId, UUID tenantId) {
        return itemRepository.findByItemIdAndTenantId(itemId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order-set item not found"));
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    private static UUID uuidVal(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try { return UUID.fromString(s.trim()); } catch (IllegalArgumentException e) { return null; }
    }
}
