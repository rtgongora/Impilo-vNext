package zw.gov.mohcc.impilo.oros.core.workflow;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Selects the per-category {@link FulfilmentWorkflow} guard for an order type. Categories with a
 * fine-grained journey (IMAGING, LAB, PROCEDURE) register a guard; categories without one
 * (e.g. PHARMACY, which is workstep-driven, or externally-owned BLOOD_BANK) resolve to empty.
 */
@Component
public class WorkflowGuardRegistry {

    private final Map<OrderType, FulfilmentWorkflow> guards = new EnumMap<>(OrderType.class);

    public WorkflowGuardRegistry(List<FulfilmentWorkflow> workflows) {
        for (FulfilmentWorkflow wf : workflows) {
            guards.put(wf.orderType(), wf);
        }
    }

    /** The guard for {@code orderType}, or empty if the category has no fine-grained journey. */
    public Optional<FulfilmentWorkflow> forType(OrderType orderType) {
        return Optional.ofNullable(guards.get(orderType));
    }

    /** The guard for {@code orderType}, or throw if the category has no fine-grained journey. */
    public FulfilmentWorkflow require(OrderType orderType) {
        return forType(orderType).orElseThrow(() -> new IllegalStateException(
                "No fine-grained fulfilment workflow is defined for order type " + orderType));
    }

    /** Whether {@code orderType} has a fine-grained workflow guard. */
    public boolean hasGuard(OrderType orderType) {
        return guards.containsKey(orderType);
    }
}
