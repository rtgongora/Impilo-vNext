package zw.gov.mohcc.impilo.oros.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.integration.VarapiClient;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

/**
 * Promotes a {@code DRAFT} order to {@code PLACED} (submitted), performing the diagnostic/imaging
 * submit-time side effects:
 *
 * <ul>
 *   <li>For {@code IMAGING} orders, reserve a RIS-style accession number (idempotent — never
 *       re-reserved if one is already present).</li>
 *   <li>Resolve the referring provider's display name from VARAPI when a provider ID is supplied
 *       (honest fallback: keeps any client-supplied name if VARAPI is not configured/unreachable).</li>
 *   <li>Initialize the fine-grained imaging workflow at {@code RECEIVED}.</li>
 *   <li>Drive the coarse canonical status {@code DRAFT → PLACED} via {@link OrderStateMachine}
 *       (which emits {@code ORDER_PLACED}).</li>
 * </ul>
 *
 * <p>Routing, workstep generation, and the SLA timer are orchestrated by the controller after
 * submit, exactly as for the legacy immediate-place flow.</p>
 */
@Service
public class OrderSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(OrderSubmissionService.class);

    private final OrderStateMachine stateMachine;
    private final AccessionNumberService accessionNumberService;
    private final VarapiClient varapiClient;
    private final ImagingWorkflowService imagingWorkflowService;
    private final FulfilmentWorkflowService fulfilmentWorkflowService;

    public OrderSubmissionService(OrderStateMachine stateMachine,
                                  AccessionNumberService accessionNumberService,
                                  VarapiClient varapiClient,
                                  ImagingWorkflowService imagingWorkflowService,
                                  FulfilmentWorkflowService fulfilmentWorkflowService) {
        this.stateMachine = stateMachine;
        this.accessionNumberService = accessionNumberService;
        this.varapiClient = varapiClient;
        this.imagingWorkflowService = imagingWorkflowService;
        this.fulfilmentWorkflowService = fulfilmentWorkflowService;
    }

    /**
     * Submit a draft order.
     *
     * @throws IllegalStateException if the order is not in {@code DRAFT}
     */
    @Transactional
    public OrderEntity submit(String orderId) {
        OrderEntity order = stateMachine.getOrder(orderId);

        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new IllegalStateException(
                    "Order " + orderId + " cannot be submitted from status " + order.getStatus());
        }

        // Reserve a RIS/LIS-style accession (lab) number at submit for the categories that use one
        // (idempotent — never re-reserved). Procedures are tracked by order id, not an accession.
        if ((order.getOrderType() == OrderType.IMAGING || order.getOrderType() == OrderType.LAB)
                && order.getAccessionNumber() == null) {
            String accession = accessionNumberService.reserve(order.getTenantId(), order.getFacilityId());
            order.setAccessionNumber(accession);
            log.info("Accession reserved at submit: orderId={}, type={}, accession={}",
                    orderId, order.getOrderType(), accession);
        }

        // Resolve the referring provider's display name from VARAPI when supplied (honest fallback:
        // keep any client-supplied name if VARAPI is not configured/unreachable). Applies to all
        // categories, not just imaging.
        String providerId = order.getReferringProviderId();
        if (providerId != null && !providerId.isBlank()) {
            varapiClient.lookupProviderName(providerId).ifPresent(order::setReferringProviderName);
        }

        if (order.getOrderType() == OrderType.IMAGING) {
            // Imaging carries dedicated side-effects (PACS study linkage), so it keeps its own
            // service; this also writes the shared workflow_state in lockstep.
            imagingWorkflowService.initializeReceived(order, "submitted");
        } else {
            // Lab/procedure (and any other guarded category) enter their fine-grained journey at
            // the category entry state; no-op for categories without a guard (e.g. pharmacy).
            fulfilmentWorkflowService.initialize(order, "submitted");
        }

        // Coarse DRAFT -> PLACED; saves the entity (carrying accession/name/imagingState) and
        // emits ORDER_PLACED.
        order = stateMachine.transition(order, OrderStatus.PLACED);

        log.info("Order submitted: orderId={}, type={}, accession={}",
                orderId, order.getOrderType(), order.getAccessionNumber());
        return order;
    }
}
