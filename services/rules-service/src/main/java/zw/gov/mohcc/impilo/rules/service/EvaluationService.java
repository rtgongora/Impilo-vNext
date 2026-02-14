package zw.gov.mohcc.impilo.rules.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.rules.api.dto.DecisionResult;
import zw.gov.mohcc.impilo.rules.api.dto.EvaluateRequest;
import zw.gov.mohcc.impilo.rules.api.dto.EvaluateResponse;
import zw.gov.mohcc.impilo.rules.domain.DecisionLogEntity;
import zw.gov.mohcc.impilo.rules.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.rules.domain.RuleEntity;
import zw.gov.mohcc.impilo.rules.engine.RuleExpressionInvalidException;
import zw.gov.mohcc.impilo.rules.engine.SimpleRuleEvaluator;
import zw.gov.mohcc.impilo.rules.repository.DecisionLogRepository;
import zw.gov.mohcc.impilo.rules.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.rules.repository.RuleRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final RuleRepository ruleRepository;
    private final DecisionLogRepository decisionLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SimpleRuleEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public EvaluationService(RuleRepository ruleRepository,
                             DecisionLogRepository decisionLogRepository,
                             OutboxEventRepository outboxEventRepository,
                             SimpleRuleEvaluator evaluator,
                             ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.decisionLogRepository = decisionLogRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EvaluateResponse evaluate(EvaluateRequest request, RequestContext ctx) {
        List<RuleEntity> rules = ruleRepository.findByTenantIdAndEnabledTrue(ctx.tenantId());
        String factsJson = toJson(request.facts());
        List<DecisionResult> decisions = new ArrayList<>();

        for (RuleEntity rule : rules) {
            String outcome;
            String reason;

            try {
                boolean matched = evaluator.evaluate(rule.getExpression(), request.facts());
                outcome = matched ? "MATCH" : "NO_MATCH";
                reason = matched
                        ? "Expression evaluated to true"
                        : "Expression evaluated to false";
            } catch (RuleExpressionInvalidException e) {
                outcome = "ERROR";
                reason = "Invalid expression: " + e.getMessage();
                log.warn("Rule {} has invalid expression: {}", rule.getId(), e.getMessage());
            } catch (Exception e) {
                outcome = "ERROR";
                reason = "Evaluation error: " + e.getMessage();
                log.error("Unexpected error evaluating rule {}", rule.getId(), e);
            }

            // Record decision log
            DecisionLogEntity logEntry = new DecisionLogEntity();
            logEntry.setRuleId(rule.getId());
            logEntry.setOutcome(outcome);
            logEntry.setReason(reason);
            logEntry.setFactsJson(factsJson);
            logEntry.setTenantId(ctx.tenantId());
            logEntry.setPodId(ctx.podId());
            decisionLogRepository.save(logEntry);

            // Record outbox event
            OutboxEventEntity outboxEvent = new OutboxEventEntity();
            outboxEvent.setTenantId(ctx.tenantId());
            outboxEvent.setPodId(ctx.podId());
            outboxEvent.setCorrelationId(ctx.correlationId());
            outboxEvent.setEventType("rules.evaluation.completed");
            outboxEvent.setSchemaVersion(1);
            outboxEvent.setOccurredAt(OffsetDateTime.now());
            outboxEvent.setPayloadJson(toJson(Map.of(
                    "ruleId", rule.getId(),
                    "ruleName", rule.getName(),
                    "outcome", outcome,
                    "reason", reason
            )));
            outboxEventRepository.save(outboxEvent);

            decisions.add(new DecisionResult(
                    rule.getId(),
                    rule.getName(),
                    outcome,
                    reason
            ));
        }

        return new EvaluateResponse(decisions);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
