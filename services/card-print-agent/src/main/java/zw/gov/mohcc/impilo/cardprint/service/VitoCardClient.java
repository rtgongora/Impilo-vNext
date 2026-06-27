package zw.gov.mohcc.impilo.cardprint.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.cardprint.config.CardPrintProperties;

import java.util.Map;
import java.util.UUID;

/**
 * Internal HTTP client for VITO's SMART-card lifecycle API.
 *
 * <p>The print agent calls {@code POST /v1/cards/{cardId}/print} to drive the card's
 * REQUESTED &rarr; PRINTED transition and provision the secure-element public key generated
 * during personalisation (G032). Without a real public key VITO fails activation closed, so
 * this callback is what makes the card usable.
 *
 * <p>The agent is Kafka-driven (no inbound HTTP request to forward trust headers from), so it
 * presents the internal trust headers itself: {@code X-Access-Mode: INTERNAL} plus tenant,
 * correlation and actor context. VITO's {@code TrustHeaderFilter} accepts internal callers with
 * only tenant + correlation, and {@code requireInternal} blocks anything in EXTERNAL mode.
 */
@Component
public class VitoCardClient {

    private static final Logger log = LoggerFactory.getLogger(VitoCardClient.class);

    private final RestClient restClient;
    private final String actorId;

    public VitoCardClient(CardPrintProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getVito().getBaseUrl())
                .build();
        this.actorId = properties.getVito().getActorId();
    }

    /**
     * Confirms a card has been printed and provisions its secure-element public key.
     *
     * @param tenantId  owning tenant of the card (must match the card's tenant in VITO)
     * @param cardId    VITO card id
     * @param publicKey PEM-encoded secure-element public key generated at personalisation
     */
    public void confirmPrinted(UUID tenantId, long cardId, String publicKey) {
        String correlationId = UUID.randomUUID().toString();
        restClient.post()
                .uri("/v1/cards/{cardId}/print", cardId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Access-Mode", "INTERNAL")
                .header("X-Tenant-Id", tenantId != null ? tenantId.toString() : "")
                .header("X-Correlation-Id", correlationId)
                .header("X-Actor-Id", actorId)
                .header("X-Actor-Type", "SERVICE")
                .body(Map.of("publicKey", publicKey))
                .retrieve()
                .toBodilessEntity();
        log.info("VITO: provisioned public key and confirmed PRINTED for card {} (tenant={})",
                cardId, tenantId);
    }
}
