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
        TrustContext ctx = TrustContextHolder.require();
        return orderRepository.search(ctx.tenantId(),
                blankToNull(patientCpid), blankToNull(requester), status, orderType);
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

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
