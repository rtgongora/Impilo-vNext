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

    public OrderSubmissionService(OrderStateMachine stateMachine,
                                  AccessionNumberService accessionNumberService,
                                  VarapiClient varapiClient,
                                  ImagingWorkflowService imagingWorkflowService) {
        this.stateMachine = stateMachine;
        this.accessionNumberService = accessionNumberService;
        this.varapiClient = varapiClient;
        this.imagingWorkflowService = imagingWorkflowService;
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

        if (order.getOrderType() == OrderType.IMAGING) {
            if (order.getAccessionNumber() == null) {
                String accession = accessionNumberService.reserve(order.getTenantId(), order.getFacilityId());
                order.setAccessionNumber(accession);
                log.info("Accession reserved at submit: orderId={}, accession={}", orderId, accession);
            }

            String providerId = order.getReferringProviderId();
            if (providerId != null && !providerId.isBlank()) {
                varapiClient.lookupProviderName(providerId)
                        .ifPresent(order::setReferringProviderName);
            }

            // Entry-point imaging state; mutates entity + writes IMAGING_STATE_RECEIVED event.
            imagingWorkflowService.initializeReceived(order, "submitted");
        }

        // Coarse DRAFT -> PLACED; saves the entity (carrying accession/name/imagingState) and
        // emits ORDER_PLACED.
        order = stateMachine.transition(order, OrderStatus.PLACED);

        log.info("Order submitted: orderId={}, type={}, accession={}",
                orderId, order.getOrderType(), order.getAccessionNumber());
        return order;
    }
}
