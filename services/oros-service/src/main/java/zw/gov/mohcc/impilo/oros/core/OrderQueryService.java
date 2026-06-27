package zw.gov.mohcc.impilo.oros.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Read-side queries over orders, tenant-scoped from the trust context.
 *
 * <p>Backs the order-tracking list and requester views: filter by client (patient CPID),
 * requester (placing actor or referring provider), coarse status, and order type.</p>
 */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<OrderEntity> search(String patientCpid, String requester,
                                    OrderStatus status, OrderType orderType) {
        return search(patientCpid, requester, status, orderType, null);
    }

    /** Order search with an additional encounter filter (backs the encounter Orders & Results panel). */
    public List<OrderEntity> search(String patientCpid, String requester,
                                    OrderStatus status, OrderType orderType, String encounterRef) {
        TrustContext ctx = TrustContextHolder.require();
        return orderRepository.search(ctx.tenantId(),
                blankToNull(patientCpid), blankToNull(requester), status, orderType,
                blankToNull(encounterRef));
    }

    /**
     * Imaging-team worklist for the current facility, filtered by fine-grained imaging state.
     * When no states are supplied, defaults to the active pre-report intake/acquisition states.
     */
    public List<OrderEntity> imagingWorklist(Collection<ImagingWorkflowState> states) {
        TrustContext ctx = TrustContextHolder.require();
        Collection<ImagingWorkflowState> filter = (states == null || states.isEmpty())
                ? EnumSet.of(ImagingWorkflowState.RECEIVED, ImagingWorkflowState.ACCEPTED,
                ImagingWorkflowState.SCHEDULED, ImagingWorkflowState.ARRIVED,
                ImagingWorkflowState.IN_PROGRESS)
                : states;
        return orderRepository.findByTenantIdAndFacilityIdAndOrderTypeAndImagingStateInOrderByUpdatedAtDesc(
                ctx.tenantId(), ctx.facilityId(), OrderType.IMAGING, filter);
    }

    /**
     * Category-agnostic fulfilment worklist for the current facility, filtered by the generalised
     * {@code workflow_state}. When no states are supplied, defaults to the active (pre-release)
     * states for the order type — lab specimen/analysis states, procedure scheduling/performance
     * states, etc.
     */
    public List<OrderEntity> workflowWorklist(OrderType orderType, Collection<String> states) {
        TrustContext ctx = TrustContextHolder.require();
        Collection<String> filter = (states == null || states.isEmpty())
                ? defaultActiveStates(orderType) : states;
        return orderRepository.findByTenantIdAndFacilityIdAndOrderTypeAndWorkflowStateInOrderByUpdatedAtDesc(
                ctx.tenantId(), ctx.facilityId(), orderType, filter);
    }

    private static Collection<String> defaultActiveStates(OrderType orderType) {
        return switch (orderType) {
            case LAB -> List.of("RECEIVED", "ACCEPTED", "AWAITING_COLLECTION", "COLLECTED",
                    "DISPATCHED", "IN_TRANSIT", "RECEIVED_IN_LAB", "IN_PROGRESS");
            case PROCEDURE -> List.of("RECEIVED", "ACCEPTED", "SCHEDULED", "ARRIVED",
                    "IN_PROGRESS", "PERFORMED", "REPORT_PENDING");
            case IMAGING -> List.of("RECEIVED", "ACCEPTED", "SCHEDULED", "ARRIVED",
                    "IN_PROGRESS", "PERFORMED", "AWAITING_IMAGES", "IMAGES_LINKED", "AWAITING_REPORT");
            default -> List.of("RECEIVED", "ACCEPTED", "IN_PROGRESS");
        };
    }

    /**
     * Requester results-inbox: orders with results ready to review for the given requester
     * (defaults to the current actor when none supplied).
     */
    public List<OrderEntity> resultsInbox(String requester) {
        TrustContext ctx = TrustContextHolder.require();
        String who = blankToNull(requester) != null ? requester : ctx.actorId();
        return orderRepository.resultsInbox(ctx.tenantId(), who,
                EnumSet.of(OrderStatus.RESULT_AVAILABLE, OrderStatus.RELEASED, OrderStatus.REVIEWED),
                EnumSet.of(ImagingWorkflowState.PRELIMINARY_REPORT, ImagingWorkflowState.FINAL_REPORT,
                        ImagingWorkflowState.RELEASED, ImagingWorkflowState.AMENDED,
                        ImagingWorkflowState.ACKNOWLEDGED));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
