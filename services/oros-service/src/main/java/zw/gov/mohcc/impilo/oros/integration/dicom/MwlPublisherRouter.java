package zw.gov.mohcc.impilo.oros.integration.dicom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.oros.domain.MwlMode;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches MWL publication to the configured strategy ({@code oros.integration.dicom.mwl.mode} =
 * OFF | REST | DIMSE). OFF is the honest NOT_LIVE default (mirrored at {@code /admin/integrations}).
 * Best-effort: publication never blocks scheduling.
 */
@Service
public class MwlPublisherRouter {

    private static final Logger log = LoggerFactory.getLogger(MwlPublisherRouter.class);

    private final Map<MwlMode, MwlPublisher> publishers = new EnumMap<>(MwlMode.class);
    private final MwlMode mode;

    public MwlPublisherRouter(List<MwlPublisher> publisherBeans,
                              @Value("${oros.integration.dicom.mwl.mode:OFF}") String mode) {
        for (MwlPublisher p : publisherBeans) {
            publishers.put(p.mode(), p);
        }
        this.mode = parseMode(mode);
    }

    public void publish(OrderEntity order, String modality) {
        if (mode == MwlMode.OFF) {
            return;
        }
        MwlPublisher publisher = publishers.get(mode);
        if (publisher == null) {
            log.warn("MWL mode {} configured but no publisher available (orderId={})", mode, order.getOrderId());
            return;
        }
        publisher.publish(order, modality);
    }

    private static MwlMode parseMode(String mode) {
        try {
            return MwlMode.valueOf(mode.trim().toUpperCase());
        } catch (Exception e) {
            return MwlMode.OFF;
        }
    }
}
