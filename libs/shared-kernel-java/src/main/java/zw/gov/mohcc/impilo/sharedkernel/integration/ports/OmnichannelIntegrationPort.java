package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import zw.gov.mohcc.impilo.sharedkernel.integration.ports.CanonicalReferences.*;

import java.util.Map;

/**
 * Canonical omnichannel comms port. Adapters: SmsProviderAdapter,
 * WhatsAppProviderAdapter, EmailProviderAdapter, IvrProviderAdapter,
 * PushNotificationAdapter, NativeCommsAdapter.
 */
public interface OmnichannelIntegrationPort extends Adapter {

    OperationResult<MessageAck>            sendMessage(MessageRequest req, OperationContext ctx);
    OperationResult<MessageDeliveryStatus> getDeliveryStatus(String messageId, OperationContext ctx);

    enum Channel { SMS, WHATSAPP, EMAIL, IVR, PUSH }

    record MessageRequest(Channel channel, String toAddress, String templateId, Map<String, String> templateVars,
                          String idempotencyKey) {}
    record MessageAck(String messageId, String providerReference, String status) {}
    record MessageDeliveryStatus(String messageId, String status, String providerReference, String failureReason) {}
}
