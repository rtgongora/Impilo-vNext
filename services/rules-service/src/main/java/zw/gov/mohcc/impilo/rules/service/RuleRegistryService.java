package zw.gov.mohcc.impilo.rules.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.rules.api.dto.*;
import zw.gov.mohcc.impilo.rules.domain.*;
import zw.gov.mohcc.impilo.rules.engine.SimpleRuleEvaluator;
import zw.gov.mohcc.impilo.rules.engine.RuleExpressionInvalidException;
import zw.gov.mohcc.impilo.rules.repository.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class RuleRegistryService {

    private static final Logger log = LoggerFactory.getLogger(RuleRegistryService.class);

    private final RuleService ruleService;
    private final RuleVersionRepository versionRepository;
    private final RuleActivationRepository activationRepository;
    private final EvaluationAuditRepository auditRepository;
    private final SimpleRuleEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public RuleRegistryService(RuleService ruleService,
                               RuleVersionRepository versionRepository,
                               RuleActivationRepository activationRepository,
                               EvaluationAuditRepository auditRepository,
                               SimpleRuleEvaluator evaluator,
                               ObjectMapper objectMapper) {
        this.ruleService = ruleService;
        this.versionRepository = versionRepository;
        this.activationRepository = activationRepository;
        this.auditRepository = auditRepository;
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public VersionResponse createVersion(String key, CreateVersionRequest request, RequestContext ctx) {
        RuleEntity rule = ruleService.findByKeyOrThrow(key, ctx.tenantId());

        int nextVersion = versionRepository.findTopByRuleIdOrderByVersionDesc(rule.getId())
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        RuleVersionEntity version = new RuleVersionEntity();
        version.setRuleId(rule.getId());
        version.setVersion(nextVersion);
        version.setDslText(request.dslText());
        version = versionRepository.save(version);

        Map<String, Object> payload = new HashMap<>();
        payload.put("ruleKey", key);
        payload.put("ruleId", rule.getId());
        payload.put("versionId", version.getId());
        payload.put("version", version.getVersion());
        ruleService.persistOutboxEvent("impilo.rules.version.created.v1", payload, ctx);

        return new VersionResponse(
                version.getId(), version.getRuleId(), version.getVersion(),
                version.getDslText(), version.getCreatedAt());
    }

    @Transactional
    public ActivationResponse activate(String key, int versionNum, RequestContext ctx) {
        RuleEntity rule = ruleService.findByKeyOrThrow(key, ctx.tenantId());

        RuleVersionEntity version = versionRepository.findByRuleIdAndVersion(rule.getId(), versionNum)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Version " + versionNum + " not found for rule " + key));

        // Close current activation
        activationRepository.findByRuleIdAndActiveToIsNull(rule.getId())
                .ifPresent(current -> {
                    current.setActiveTo(OffsetDateTime.now());
                    activationRepository.save(current);
                });

        // Create new activation
        RuleActivationEntity activation = new RuleActivationEntity();
        activation.setRuleId(rule.getId());
        activation.setVersionId(version.getId());
        activation.setActiveFrom(OffsetDateTime.now());
        activation = activationRepository.save(activation);

        // Set rule status to ACTIVE
        rule.setStatus("ACTIVE");
        ruleService.save(rule);

        Map<String, Object> payload = new HashMap<>();
        payload.put("ruleKey", key);
        payload.put("ruleId", rule.getId());
        payload.put("versionId", version.getId());
        payload.put("version", versionNum);
        ruleService.persistOutboxEvent("impilo.rules.rule.activated.v1", payload, ctx);

        return new ActivationResponse(key, version.getId(), versionNum, activation.getActiveFrom());
    }

    @Transactional
    public RuleResponse deactivate(String key, RequestContext ctx) {
        RuleEntity rule = ruleService.findByKeyOrThrow(key, ctx.tenantId());

        // Close current activation
        activationRepository.findByRuleIdAndActiveToIsNull(rule.getId())
                .ifPresent(current -> {
                    current.setActiveTo(OffsetDateTime.now());
                    activationRepository.save(current);
                });

        rule.setStatus("INACTIVE");
        rule = ruleService.save(rule);

        Map<String, Object> payload = new HashMap<>();
        payload.put("ruleKey", key);
        payload.put("ruleId", rule.getId());
        ruleService.persistOutboxEvent("impilo.rules.rule.deactivated.v1", payload, ctx);

        return ruleService.toResponse(rule);
    }

    @Transactional
    public EvaluateRuleResponse evaluate(String key, EvaluateRequest request, RequestContext ctx) {
        RuleEntity rule = ruleService.findByKeyOrThrow(key, ctx.tenantId());

        if (!"ACTIVE".equals(rule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Rule '" + key + "' is not active (status: " + rule.getStatus() + ")");
        }

        // Find current activation
        RuleActivationEntity activation = activationRepository
                .findByRuleIdAndActiveToIsNull(rule.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Rule '" + key + "' has no active version"));

        RuleVersionEntity version = versionRepository.findById(activation.getVersionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Active version not found for rule " + key));

        // Evaluate
        String outcome;
        String reason;
        try {
            boolean matched = evaluator.evaluate(version.getDslText(), request.facts());
            outcome = matched ? "MATCH" : "NO_MATCH";
            reason = matched ? "Expression evaluated to true" : "Expression evaluated to false";
        } catch (RuleExpressionInvalidException e) {
            outcome = "ERROR";
            reason = "Invalid expression: " + e.getMessage();
        }

        // Compute input hash
        String inputHash = computeSha256(ruleService.toJson(request.facts()));

        // Write audit row
        String resultJson = ruleService.toJson(Map.of("outcome", outcome, "reason", reason));

        EvaluationAuditEntity audit = new EvaluationAuditEntity();
        audit.setRuleKey(key);
        audit.setVersion(version.getVersion());
        audit.setInputHash(inputHash);
        audit.setResultJson(resultJson);
        audit.setTenantId(ctx.tenantId());
        audit.setPodId(ctx.podId());
        audit.setCorrelationId(ctx.correlationId());
        audit = auditRepository.save(audit);

        // Emit outbox event
        Map<String, Object> payload = new HashMap<>();
        payload.put("ruleKey", key);
        payload.put("version", version.getVersion());
        payload.put("outcome", outcome);
        payload.put("auditId", audit.getId());
        payload.put("inputHash", inputHash);
        ruleService.persistOutboxEvent("impilo.rules.evaluated.v1", payload, ctx);

        return new EvaluateRuleResponse(key, version.getVersion(), outcome, reason, audit.getId());
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
