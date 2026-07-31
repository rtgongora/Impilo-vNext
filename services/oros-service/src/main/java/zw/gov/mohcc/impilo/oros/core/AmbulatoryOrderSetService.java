package zw.gov.mohcc.impilo.oros.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderSetDefinitionEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderSetInstanceEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderSetItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderSetDefinitionRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderSetInstanceRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderSetItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ambulatory order sets — definitions live in OROS; batch place loops {@link OrderStateMachine#placeOrder}.
 */
@Service
public class AmbulatoryOrderSetService {

    private final OrderSetDefinitionRepository definitionRepository;
    private final OrderSetItemRepository itemRepository;
    private final OrderSetInstanceRepository instanceRepository;
    private final OrderStateMachine stateMachine;

    public AmbulatoryOrderSetService(OrderSetDefinitionRepository definitionRepository,
                                     OrderSetItemRepository itemRepository,
                                     OrderSetInstanceRepository instanceRepository,
                                     OrderStateMachine stateMachine) {
        this.definitionRepository = definitionRepository;
        this.itemRepository = itemRepository;
        this.instanceRepository = instanceRepository;
        this.stateMachine = stateMachine;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDefinitions() {
        TrustContext ctx = TrustContextHolder.require();
        List<OrderSetDefinitionEntity> defs =
                definitionRepository.findByTenantIdAndActiveTrueOrderBySetNameAsc(ctx.tenantId());
        // Seeded catalogue uses a fixed preview tenant; fall back so clinicians still see sets.
        if (defs.isEmpty()) {
            defs = definitionRepository.findByActiveTrueOrderBySetNameAsc();
        }
        return defs.stream().map(def -> {
            Map<String, Object> m = toDef(def);
            m.put("items", itemRepository.findBySetIdOrderBySortOrderAsc(def.getSetId())
                    .stream().map(this::toItem).collect(Collectors.toList()));
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDefinition(UUID setId) {
        TrustContext ctx = TrustContextHolder.require();
        OrderSetDefinitionEntity def = definitionRepository.findByTenantIdAndSetId(ctx.tenantId(), setId)
                .or(() -> definitionRepository.findBySetId(setId))
                .orElseThrow(() -> new IllegalArgumentException("Order set not found: " + setId));
        Map<String, Object> m = toDef(def);
        m.put("items", itemRepository.findBySetIdOrderBySortOrderAsc(def.getSetId())
                .stream().map(this::toItem).collect(Collectors.toList()));
        return m;
    }

    /**
     * Places one real OROS order per set item. Fulfilment stays on each order — this is not pathway-apply.
     */
    @Transactional
    public Map<String, Object> batchPlace(UUID setId, String patientCpid, String encounterRef, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        OrderSetDefinitionEntity def = definitionRepository.findByTenantIdAndSetId(ctx.tenantId(), setId)
                .or(() -> definitionRepository.findBySetId(setId))
                .orElseThrow(() -> new IllegalArgumentException("Order set not found: " + setId));
        List<OrderSetItemEntity> items = itemRepository.findBySetIdOrderBySortOrderAsc(setId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order set has no items: " + setId);
        }

        List<String> orderIds = new ArrayList<>();
        for (OrderSetItemEntity item : items) {
            OrderType type = OrderType.valueOf(item.getOrderType().toUpperCase(Locale.ROOT));
            OrderPriority priority = OrderPriority.valueOf(
                    (item.getPriority() == null ? "ROUTINE" : item.getPriority()).toUpperCase(Locale.ROOT));
            List<OrderStateMachine.OrderItemData> orderItems = List.of(
                    OrderStateMachine.OrderItemData.lab(
                            item.getZiboCode(),
                            item.getDisplayName(),
                            item.getQuantity() == null ? 1 : item.getQuantity(),
                            item.getInstructions(),
                            null,
                            null));
            OrderEntity order = stateMachine.placeOrder(
                    ctx.facilityId(),
                    patientCpid,
                    type,
                    priority,
                    item.getZiboCode(),
                    encounterRef,
                    notes,
                    RequestSource.INTERNAL,
                    "order-set:" + def.getSetCode(),
                    orderItems);
            orderIds.add(order.getOrderId());
        }

        OrderSetInstanceEntity instance = new OrderSetInstanceEntity();
        instance.setTenantId(ctx.tenantId());
        instance.setSetId(setId);
        instance.setPatientCpid(patientCpid);
        instance.setEncounterRef(encounterRef);
        instance.setFacilityId(ctx.facilityId());
        instance.setPlacedBy(ctx.actorId());
        instance.setOrderIdsJson(String.join(",", orderIds));
        instance.setNotes(notes);
        instance = instanceRepository.save(instance);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instance_id", instance.getInstanceId().toString());
        out.put("set_id", setId.toString());
        out.put("set_code", def.getSetCode());
        out.put("patient_cpid", patientCpid);
        out.put("order_ids", orderIds);
        return out;
    }

    private Map<String, Object> toDef(OrderSetDefinitionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("set_id", e.getSetId().toString());
        m.put("set_code", e.getSetCode());
        m.put("set_name", e.getSetName());
        m.put("indication", e.getIndication());
        m.put("description", e.getDescription());
        return m;
    }

    private Map<String, Object> toItem(OrderSetItemEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item_id", e.getItemId().toString());
        m.put("order_type", e.getOrderType());
        m.put("zibo_code", e.getZiboCode());
        m.put("display_name", e.getDisplayName());
        m.put("priority", e.getPriority());
        m.put("quantity", e.getQuantity());
        m.put("instructions", e.getInstructions());
        m.put("removable", e.getRemovable());
        return m;
    }
}
