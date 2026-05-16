package zw.gov.mohcc.impilo.nhume.integration.commshub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Local Comms Hub adapter — emits structured logs and returns a synthetic
 * provider reference. Replace with an HTTP-backed implementation when
 * {@code impilo.nhume.comms-hub.base-url} is wired and Comms Hub is online.
 */
@Component
@ConditionalOnMissingBean(CommsHubClient.class)
public class LoggingCommsHubClient implements CommsHubClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingCommsHubClient.class);

    @Value("${impilo.nhume.comms-hub.enabled:true}")
    private boolean enabled;

    @Override
    public DispatchResult dispatch(String eventCode, String channel, String recipientRef,
                                   String templateCode, Map<String, Object> data) {
        if (!enabled) {
            log.debug("Comms Hub disabled — skipping eventCode={} channel={}", eventCode, channel);
            return DispatchResult.skipped("comms-hub-disabled");
        }
        if (recipientRef == null || recipientRef.isBlank()) {
            return DispatchResult.skipped("missing-recipient");
        }
        String providerRef = "nhume:" + eventCode + ":" + UUID.randomUUID();
        log.info("[comms-hub] event={} channel={} template={} recipient={} provider_ref={} data_keys={}",
                eventCode, channel, templateCode, recipientRef, providerRef,
                data == null ? 0 : data.size());
        return DispatchResult.sent(providerRef);
    }
}
