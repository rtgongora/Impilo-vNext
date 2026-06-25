package zw.gov.mohcc.impilo.oros.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

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

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
