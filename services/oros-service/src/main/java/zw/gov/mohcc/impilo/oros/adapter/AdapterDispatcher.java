package zw.gov.mohcc.impilo.oros.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.oros.adapter.lims.LimsAdapter;
import zw.gov.mohcc.impilo.oros.adapter.pacs.PacsAdapter;
import zw.gov.mohcc.impilo.oros.adapter.pharmacy.PharmacyAdapter;
import zw.gov.mohcc.impilo.oros.domain.RouteStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.RoutingEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.RoutingRepository;

/**
 * Central dispatcher that routes orders to the appropriate external system adapter.
 *
 * <p>Acts as a facade over the individual adapters (LIMS, PACS, Pharmacy),
 * selecting the correct adapter based on the routing entity's target system.
 * Handles dispatch errors by marking the route as FAILED with the error message,
 * allowing the routing engine to retry later.</p>
 *
 * <h3>Supported Targets</h3>
 * <ul>
 *   <li>{@code LIMS} -- dispatches to {@link LimsAdapter}</li>
 *   <li>{@code PACS} -- dispatches to {@link PacsAdapter}</li>
 *   <li>{@code PHARMACY_EXT} -- dispatches to {@link PharmacyAdapter}</li>
 *   <li>{@code INTERNAL} -- no external dispatch needed</li>
 *   <li>{@code OTHER} -- logged as unsupported</li>
 * </ul>
 */
@Service
public class AdapterDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AdapterDispatcher.class);

    private final LimsAdapter limsAdapter;
    private final PacsAdapter pacsAdapter;
    private final PharmacyAdapter pharmacyAdapter;
    private final RoutingRepository routingRepository;

    /**
     * Constructs the AdapterDispatcher with all available adapters.
     *
     * @param limsAdapter       the LIMS adapter for laboratory order dispatch
     * @param pacsAdapter       the PACS adapter for imaging order dispatch
     * @param pharmacyAdapter   the pharmacy adapter for medication order dispatch
     * @param routingRepository repository for updating route status after dispatch
     */
    public AdapterDispatcher(LimsAdapter limsAdapter,
                             PacsAdapter pacsAdapter,
                             PharmacyAdapter pharmacyAdapter,
                             RoutingRepository routingRepository) {
        this.limsAdapter = limsAdapter;
        this.pacsAdapter = pacsAdapter;
        this.pharmacyAdapter = pharmacyAdapter;
        this.routingRepository = routingRepository;
    }

    /**
     * Dispatch an order to the external system identified by the routing entity's target.
     *
     * <p>Selects the appropriate adapter based on the route target and delegates
     * the order. On success, the route status is updated to SENT. On failure,
     * the route is marked FAILED with the error message for later retry.</p>
     *
     * @param route the routing entity specifying the target system and current status
     * @param order the order entity to dispatch
     * @throws RuntimeException if the dispatch fails (route will be marked FAILED)
     */
    public void dispatch(RoutingEntity route, OrderEntity order) {
        log.info("Dispatching order {} to {} via {} mode",
                order.getOrderId(), route.getRouteTarget(), route.getAdapterMode());

        try {
            switch (route.getRouteTarget()) {
                case LIMS -> limsAdapter.sendOrder(order, route);

                case PACS -> pacsAdapter.sendImagingOrder(order, route);

                case PHARMACY_EXT -> pharmacyAdapter.sendPharmacyOrder(order, route);

                case INTERNAL -> {
                    // Internal routing does not require adapter dispatch
                    route.setStatus(RouteStatus.ACKNOWLEDGED);
                    routingRepository.save(route);
                    log.info("Order {} routed internally, no adapter dispatch needed", order.getOrderId());
                    return;
                }

                default -> {
                    String msg = "Unsupported route target: " + route.getRouteTarget();
                    log.error(msg);
                    route.setStatus(RouteStatus.FAILED);
                    route.setLastError(msg);
                    routingRepository.save(route);
                    return;
                }
            }

            // On successful dispatch
            route.setStatus(RouteStatus.SENT);
            routingRepository.save(route);

            log.info("Order {} dispatched successfully to {}", order.getOrderId(), route.getRouteTarget());

        } catch (Exception e) {
            log.error("Failed to dispatch order {} to {}: {}",
                    order.getOrderId(), route.getRouteTarget(), e.getMessage(), e);
            route.setStatus(RouteStatus.FAILED);
            route.setLastError(e.getMessage());
            routingRepository.save(route);
            throw new RuntimeException("Adapter dispatch failed for order " + order.getOrderId(), e);
        }
    }
}
