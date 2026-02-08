package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargingRulesetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.RulesetStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargingRulesetRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RulesetService {

    private static final Logger log = LoggerFactory.getLogger(RulesetService.class);

    private final ChargingRulesetRepository rulesetRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RulesetService(ChargingRulesetRepository rulesetRepository,
                          EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.rulesetRepository = rulesetRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public Page<ChargingRulesetEntity> listRulesets(UUID tenantId, Pageable pageable) {
        return rulesetRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional
    public ChargingRulesetEntity publish(Long rulesetId) {
        ChargingRulesetEntity ruleset = rulesetRepository.findById(rulesetId)
                .orElseThrow(() -> new IllegalArgumentException("Ruleset not found: " + rulesetId));

        if (ruleset.getStatus() != RulesetStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT rulesets can be published");
        }

        // Validate rules JSON
        try {
            objectMapper.readTree(ruleset.getRules());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid rules JSON: " + e.getMessage());
        }

        // Archive any previously published rulesets with same name
        List<ChargingRulesetEntity> published = rulesetRepository.findByTenantIdAndStatus(
                ruleset.getTenantId(), RulesetStatus.PUBLISHED);
        for (ChargingRulesetEntity existing : published) {
            if (existing.getName().equals(ruleset.getName())) {
                existing.setStatus(RulesetStatus.ARCHIVED);
                rulesetRepository.save(existing);
            }
        }

        ruleset.setStatus(RulesetStatus.PUBLISHED);
        ruleset.setPublishedAt(OffsetDateTime.now());
        ruleset = rulesetRepository.save(ruleset);

        TrustContext ctx = TrustContextHolder.require();
        publishEvent("RULESET", rulesetId.toString(), "RULESET_PUBLISHED",
                Map.of("rulesetId", rulesetId, "name", ruleset.getName(), "version", ruleset.getVersion()),
                ctx.tenantId());

        return ruleset;
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, Map<String, Object> payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
