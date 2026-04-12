package zw.gov.mohcc.impilo.coverage.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.ClaimEntity;
import zw.gov.mohcc.impilo.coverage.repository.ClaimRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes MUSHEX finance events to reconcile coverage claim payment state.
 */
@Component
@Profile("!test")
public class CoverageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CoverageEventConsumer.class);

    private final ClaimRepository claimRepository;
    private final ObjectMapper objectMapper;

    public CoverageEventConsumer(ClaimRepository claimRepository, ObjectMapper objectMapper) {
        this.claimRepository = claimRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "mushex.payment.status.changed",
                    "mushex.claim.adjudicated",
                    "mushex.settlement.batch.released"
            },
            groupId = "coverage-service"
    )
    @Transactional
    public void onMushexEvent(String payload,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               Acknowledgment acknowledgment) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String correlation = text(event, "correlation_id", "correlationId");

            switch (topic) {
                case "mushex.payment.status.changed" -> handlePaymentStatus(event, correlation);
                case "mushex.claim.adjudicated" -> handleClaimAdjudicated(event, correlation);
                case "mushex.settlement.batch.released" -> log.info(
                        "MUSHEX settlement batch released [correlation_id={}] payload={}",
                        correlation, payload);
                default -> log.debug("Ignoring unexpected topic {}", topic);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse MUSHEX event on {}: {}", topic, e.getMessage(), e);
        } finally {
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    private void handlePaymentStatus(JsonNode event, String correlationId) {
        String toStatus = text(event, "toStatus", "status");
        if (toStatus == null) {
            log.warn("MUSHEX payment status event missing toStatus/status [correlation_id={}]", correlationId);
            return;
        }
        String normalized = toStatus.toUpperCase();
        if (!"PAID".equals(normalized) && !"SETTLED".equals(normalized)) {
            log.debug("MUSHEX payment status {} does not trigger coverage claim update [correlation_id={}]",
                    toStatus, correlationId);
            return;
        }

        String claimKey = text(event, "claimId", "claim_id");
        if (claimKey == null) {
            log.info("MUSHEX payment settled without claim_id; skipping coverage claim update [correlation_id={}]",
                    correlationId);
            return;
        }

        Optional<UUID> claimUuid = parseUuid(claimKey);
        if (claimUuid.isEmpty()) {
            log.info("MUSHEX payment claim_id not a coverage UUID; skipping [correlation_id={}, claim_id={}]",
                    correlationId, claimKey);
            return;
        }

        BigDecimal amountPaid = decimal(event, "amountPaid", "amount_paid");
        Optional<ClaimEntity> claimOpt = claimRepository.findById(claimUuid.get());
        if (claimOpt.isEmpty()) {
            log.debug("No local coverage claim for mushex claim_id={} [correlation_id={}]",
                    claimKey, correlationId);
            return;
        }

        ClaimEntity claim = claimOpt.get();
        if ("ADJUDICATED".equals(claim.getStatus())) {
            claim.markPaid(amountPaid);
            claimRepository.save(claim);
            log.info("Coverage claim {} marked PAID from MUSHEX payment feedback [correlation_id={}, amount_paid={}]",
                    claim.getId(), correlationId, amountPaid);
        } else {
            log.info("Skipping PAID transition for claim {} in status {} [correlation_id={}]",
                    claim.getId(), claim.getStatus(), correlationId);
        }
    }

    private void handleClaimAdjudicated(JsonNode event, String correlationId) {
        String claimKey = text(event, "claimId", "claim_id");
        if (claimKey == null) {
            log.warn("MUSHEX claim adjudicated missing claimId [correlation_id={}]", correlationId);
            return;
        }
        Optional<UUID> claimUuid = parseUuid(claimKey);
        if (claimUuid.isEmpty()) {
            log.debug("Adjudicated claim_id is not a UUID; skipping coverage projection [correlation_id={}]",
                    correlationId);
            return;
        }

        BigDecimal insurerPayable = decimal(event, "insurerPayable", "insurer_payable");
        Optional<ClaimEntity> claimOpt = claimRepository.findById(claimUuid.get());
        if (claimOpt.isEmpty()) {
            log.debug("No coverage claim {} for adjudication event [correlation_id={}]", claimKey, correlationId);
            return;
        }

        ClaimEntity claim = claimOpt.get();
        if (!"SUBMITTED".equals(claim.getStatus())) {
            log.info("Skipping adjudication update for claim {} in status {} [correlation_id={}]",
                    claim.getId(), claim.getStatus(), correlationId);
            return;
        }
        try {
            String adjudicationJson = objectMapper.writeValueAsString(event);
            claim.markAdjudicated(insurerPayable != null ? insurerPayable : claim.getTotalAmount(),
                    adjudicationJson);
            claimRepository.save(claim);
            log.info("Coverage claim {} marked ADJUDICATED from MUSHEX [correlation_id={}]",
                    claim.getId(), correlationId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize adjudication payload for claim {}: {}", claim.getId(), e.getMessage());
        }
    }

    private static String text(JsonNode node, String... names) {
        for (String n : names) {
            if (node.hasNonNull(n) && !node.get(n).asText().isBlank()) {
                return node.get(n).asText();
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String... names) {
        for (String n : names) {
            if (node.hasNonNull(n)) {
                JsonNode v = node.get(n);
                if (v.isNumber()) {
                    return v.decimalValue();
                }
                try {
                    return new BigDecimal(v.asText());
                } catch (Exception ignored) {
                    // try next name
                }
            }
        }
        return null;
    }

    private static Optional<UUID> parseUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
