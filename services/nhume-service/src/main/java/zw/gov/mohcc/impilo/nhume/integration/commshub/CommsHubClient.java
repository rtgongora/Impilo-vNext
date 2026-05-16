package zw.gov.mohcc.impilo.nhume.integration.commshub;

import java.util.Map;

/**
 * Comms Hub adapter used by Nhume to dispatch notifications.
 *
 * <p>Nhume must not toast UI events as its only notification mechanism.
 * Every meaningful delivery transition flows through this client so that
 * the omnichannel layer (in-app, push, SMS, WhatsApp, email, USSD, IVR,
 * provider portal, dispatcher alert, Nompilo message, partner webhook)
 * can manage templates and channel selection centrally.
 *
 * <p>The default {@code LoggingCommsHubClient} captures a structured
 * record of what would be sent. Wire a real HTTP client at deploy time.
 */
public interface CommsHubClient {

    /**
     * Send a templated notification through Comms Hub.
     *
     * @param eventCode     canonical Nhume event code, e.g. {@code DELIVERY_ASSIGNED}
     * @param channel       channel hint (IN_APP, PUSH, SMS, EMAIL, WHATSAPP, VOICE, PORTAL)
     * @param recipientRef  channel-specific recipient (phone, email, actor reference)
     * @param templateCode  Comms Hub template id (e.g. {@code nhume.delivery.assigned})
     * @param data          template substitution payload
     * @return provider reference / delivery state
     */
    DispatchResult dispatch(String eventCode, String channel, String recipientRef,
                            String templateCode, Map<String, Object> data);

    record DispatchResult(String providerRef, String state) {
        public static DispatchResult queued(String providerRef) {
            return new DispatchResult(providerRef, "QUEUED");
        }
        public static DispatchResult sent(String providerRef) {
            return new DispatchResult(providerRef, "SENT");
        }
        public static DispatchResult skipped(String reason) {
            return new DispatchResult(null, "SKIPPED:" + reason);
        }
    }
}
