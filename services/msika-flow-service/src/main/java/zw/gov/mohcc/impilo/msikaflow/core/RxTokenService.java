package zw.gov.mohcc.impilo.msikaflow.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.integration.ShareSlipClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.OrderRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifies and attaches a share-slip Rx token to an order.
 *
 * <p>Replaces the previous {@code RxController.attachRxToken} stub, which returned
 * "validated and attached" for any request without calling anything. A token is now
 * only attached once share-slip-service confirms it is {@code valid}; an invalid
 * token is a 422 ({@link ValidationFailedException}), and an unreachable
 * share-slip-service is a 502 ({@link UpstreamUnavailableException}) — never a
 * silent success.</p>
 */
@Service
public class RxTokenService {

    private static final Logger log = LoggerFactory.getLogger(RxTokenService.class);

    private final ShareSlipClient shareSlipClient;
    private final OrderStateMachine stateMachine;
    private final OrderRepository orderRepository;

    public RxTokenService(ShareSlipClient shareSlipClient,
                          OrderStateMachine stateMachine,
                          OrderRepository orderRepository) {
        this.shareSlipClient = shareSlipClient;
        this.stateMachine = stateMachine;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderEntity attachToken(String orderId, String token, String actorId, String actorType) {
        OrderEntity order = stateMachine.getOrder(orderId);

        ShareSlipClient.ShareSlipVerification verification = shareSlipClient.verify(token);
        if (!verification.valid()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("orderId", orderId);
            details.put("status", verification.status());
            throw new ValidationFailedException("RX_TOKEN_INVALID",
                    "Share-slip token is not valid (status=" + verification.status() + ")", details);
        }

        order.setRxTokenRef(token);
        order.setRxTokenSubjectType(verification.subjectType());
        orderRepository.save(order);

        stateMachine.recordAuxiliaryEvent(orderId, actorId, actorType, "RX_TOKEN_ATTACHED",
                "subjectType=" + verification.subjectType());

        log.info("Rx token attached: orderId={} subjectType={}", orderId, verification.subjectType());
        return order;
    }
}
