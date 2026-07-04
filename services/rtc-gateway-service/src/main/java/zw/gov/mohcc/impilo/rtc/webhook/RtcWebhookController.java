package zw.gov.mohcc.impilo.rtc.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.rtc.RtcGatewayProperties;

import java.util.Locale;
import java.util.Map;

/**
 * LiveKit webhook ingress. External-origin: LiveKit posts
 * {@code application/webhook+json} with an HS256-signed JWT in the
 * Authorization header instead of companion trust headers — the path is
 * exempted from the companion filter chain and the OAuth2 resource server
 * (see CompanionWebhookExemptionConfig / SecurityConfig) and authenticated by
 * {@link LiveKitWebhookVerifier} instead.
 */
@RestController
@RequestMapping("/internal/v1/rtc/webhooks")
public class RtcWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RtcWebhookController.class);

    private final RtcGatewayProperties properties;
    private final LiveKitWebhookVerifier verifier;
    private final RtcWebhookTranslator translator;
    private final ObjectMapper objectMapper;

    public RtcWebhookController(RtcGatewayProperties properties,
                                LiveKitWebhookVerifier verifier,
                                RtcWebhookTranslator translator,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.verifier = verifier;
        this.translator = translator;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/livekit")
    public ResponseEntity<Map<String, Object>> livekit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String rawBody) {
        if (!properties.getWebhook().isEnabled()) {
            // 200 so LiveKit does not retry-storm a deliberately disabled ingester.
            return ResponseEntity.ok(Map.of("status", "disabled"));
        }
        verifier.verify(authorization, rawBody);

        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "RTC_WEBHOOK_INVALID_BODY", "message", "Webhook body is not valid JSON")));
        }
        if (event == null || !event.isObject()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "RTC_WEBHOOK_INVALID_BODY", "message", "Webhook body must be a JSON object")));
        }

        RtcWebhookTranslator.Outcome outcome = translator.handle(event);
        return ResponseEntity.ok(Map.of("status", outcome.name().toLowerCase(Locale.ROOT)));
    }

    @ExceptionHandler(LiveKitWebhookVerifier.WebhookVerificationException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(LiveKitWebhookVerifier.WebhookVerificationException ex) {
        log.warn("Rejected LiveKit webhook: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", Map.of("code", "RTC_WEBHOOK_UNAUTHORIZED", "message", ex.getMessage())));
    }
}
