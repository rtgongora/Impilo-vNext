package zw.gov.mohcc.impilo.oros.integration.hl7;

import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.app.Initiator;
import ca.uhn.hl7v2.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;

/**
 * HL7 v2 ORU-outbound adapter: send a final report as {@code ORU^R01} over MLLP to an external
 * system. Flag-gated by {@code oros.integration.hl7.oru-outbound.enabled} (NOT_LIVE by default,
 * mirrored at {@code /admin/integrations}); best-effort — failure is logged, never blocks reporting.
 */
@Component
public class Hl7OruSender {

    private static final Logger log = LoggerFactory.getLogger(Hl7OruSender.class);

    private final HapiContext hapiContext;
    private final Hl7OruMapper mapper;
    private final boolean enabled;
    private final String host;
    private final int port;

    public Hl7OruSender(HapiContext hapiContext, Hl7OruMapper mapper,
                        @Value("${oros.integration.hl7.oru-outbound.enabled:false}") boolean enabled,
                        @Value("${oros.integration.hl7.oru-outbound.host:localhost}") String host,
                        @Value("${oros.integration.hl7.oru-outbound.port:2576}") int port) {
        this.hapiContext = hapiContext;
        this.mapper = mapper;
        this.enabled = enabled;
        this.host = host;
        this.port = port;
    }

    /** Send the order's final report as ORU^R01. No-op when disabled. */
    public void sendFinalReport(OrderEntity order, ResultEntity result) {
        if (!enabled) {
            return;
        }
        Connection connection = null;
        try {
            Message message = hapiContext.getPipeParser().parse(mapper.buildOru(order, result));
            connection = hapiContext.newClient(host, port, false);
            Initiator initiator = connection.getInitiator();
            Message ack = initiator.sendAndReceive(message);
            log.info("HL7 ORU^R01 sent for orderId={} to {}:{} — ack received={}",
                    order.getOrderId(), host, port, ack != null);
        } catch (Exception e) {
            log.warn("HL7 ORU^R01 send failed for orderId={} to {}:{}: {}",
                    order.getOrderId(), host, port, e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception closeError) {
                    log.debug("Error closing HL7 connection: {}", closeError.getMessage());
                }
            }
        }
    }
}
