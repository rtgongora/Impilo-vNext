package zw.gov.mohcc.impilo.oros.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.oros.api.dto.OrderSummaryDto;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.SpecimenEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.SpecimenRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds order summaries that say what was ordered and how far it got.
 *
 * <p>The order list endpoints projected {@code OrderEntity} alone, so the EHR's results and orders
 * tabs — which declare {@code testName}, {@code collectedAt} and {@code resultedAt} — rendered
 * those columns as undefined. A clinician opening Results could see that an order existed but not
 * which test it was, whether the specimen had been taken, or whether anything had come back. A
 * missing {@code resultedAt} is not neutral on that screen: it reads as "no result yet", which is
 * an affirmative clinical claim.
 *
 * <p>The data was never missing — {@code order_items}, {@code specimens} and {@code results} hold
 * it. It was simply never joined. This joins it in three queries regardless of list length; doing
 * it per order would put an N+1 on the busiest clinical screen in the product.
 */
@Service
public class OrderSummaryProjection {

    private final OrderItemRepository orderItemRepository;
    private final SpecimenRepository specimenRepository;
    private final ResultRepository resultRepository;

    public OrderSummaryProjection(OrderItemRepository orderItemRepository,
                                  SpecimenRepository specimenRepository,
                                  ResultRepository resultRepository) {
        this.orderItemRepository = orderItemRepository;
        this.specimenRepository = specimenRepository;
        this.resultRepository = resultRepository;
    }

    public List<OrderSummaryDto> summarise(List<OrderEntity> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        Set<String> orderIds = orders.stream()
                .map(OrderEntity::getOrderId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, List<OrderItemEntity>> itemsByOrder = orderItemRepository.findByOrderIdIn(orderIds)
                .stream().collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        Map<String, List<SpecimenEntity>> specimensByOrder = specimenRepository.findByOrderIdIn(orderIds)
                .stream().collect(Collectors.groupingBy(SpecimenEntity::getOrderId));

        // Results are versioned and an order can be re-reported. The highest version is the one
        // that stands — showing an earlier version would show a superseded finding as current.
        Map<String, ResultEntity> latestResultByOrder = resultRepository.findByOrderIdIn(orderIds)
                .stream().collect(Collectors.toMap(
                        ResultEntity::getOrderId,
                        result -> result,
                        (left, right) -> Comparator.comparingInt(ResultEntity::getVersion)
                                .compare(left, right) >= 0 ? left : right));

        return orders.stream()
                .map(order -> OrderSummaryDto.from(
                        order,
                        itemsByOrder.getOrDefault(order.getOrderId(), List.of()),
                        specimensByOrder.getOrDefault(order.getOrderId(), List.of()),
                        latestResultByOrder.get(order.getOrderId())))
                .toList();
    }
}
