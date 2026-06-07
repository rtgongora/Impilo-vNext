package zw.gov.mohcc.impilo.integration.governance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.integration.governance.domain.ExternalEventSubscriptionEntity;
import zw.gov.mohcc.impilo.integration.governance.domain.WebhookSubscriptionEntity;
import zw.gov.mohcc.impilo.integration.governance.repository.GovernanceRepositories.ExternalEventSubscriptionRepository;
import zw.gov.mohcc.impilo.integration.governance.repository.GovernanceRepositories.WebhookSubscriptionRepository;

import java.util.List;
import java.util.UUID;

/**
 * Matches {@code ih_external_event_subscriptions} and delivers signed webhook payloads.
 */
@Service
public class ExternalPublishableWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ExternalPublishableWebhookDispatcher.class);

    private final ExternalEventSubscriptionRepository externalEventSubs;
    private final WebhookSubscriptionRepository webhookSubs;
    private final WebhookSigner signer;
    private final RestTemplate restTemplate;

    public ExternalPublishableWebhookDispatcher(
            ExternalEventSubscriptionRepository externalEventSubs,
            WebhookSubscriptionRepository webhookSubs,
            WebhookSigner signer,
            RestTemplate restTemplate) {
        this.externalEventSubs = externalEventSubs;
        this.webhookSubs = webhookSubs;
        this.signer = signer;
        this.restTemplate = restTemplate;
    }

    public void dispatch(String topic, String key, String payload) {
        List<ExternalEventSubscriptionEntity> subs =
                externalEventSubs.findByEventTopicAndStatus(topic, "ACTIVE");
        if (subs.isEmpty()) {
            log.debug("No active external event subscriptions for topic={}", topic);
            return;
        }

        for (ExternalEventSubscriptionEntity sub : subs) {
            webhookSubs.findById(sub.getWebhookSubscriptionId()).ifPresent(webhook -> {
                if (!webhook.isActive()) {
                    return;
                }
                deliver(webhook, topic, payload != null ? payload : "{}");
            });
        }
    }

    private void deliver(WebhookSubscriptionEntity webhook, String topic, String payload) {
        String deliveryId = UUID.randomUUID().toString();
        String secret = resolveSigningSecret(webhook.getSignatureSecretRef());
        String signature = signer.signNow(secret, payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(WebhookSigner.SIGNATURE_HEADER, signature);
        headers.add(WebhookSigner.DELIVERY_ID_HEADER, deliveryId);
        headers.add(WebhookSigner.TOPIC_HEADER, topic);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    webhook.getDeliveryUrl(),
                    new HttpEntity<>(payload, headers),
                    String.class);
            log.info("Webhook delivered topic={} url={} status={}", topic, webhook.getDeliveryUrl(), response.getStatusCode());
        } catch (Exception e) {
            log.warn("Webhook delivery failed topic={} url={}: {}", topic, webhook.getDeliveryUrl(), e.getMessage());
        }
    }

    private static String resolveSigningSecret(String secretRef) {
        if (secretRef != null && secretRef.contains("(sandbox:")) {
            return "dev-sandbox-secret";
        }
        return secretRef != null ? secretRef : "dev-sandbox-secret";
    }
}
