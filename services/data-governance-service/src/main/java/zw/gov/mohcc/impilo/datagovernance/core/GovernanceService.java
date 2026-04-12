package zw.gov.mohcc.impilo.datagovernance.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.datagovernance.api.dto.CreateDatasetRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.CreateGrantRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.DecideRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.DecideResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.ExportRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.ExportResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.PublishPolicyRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.RevokeGrantRequest;
import zw.gov.mohcc.impilo.datagovernance.domain.DatasetEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.DecisionAuditEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.GovernanceRuleEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.GrantEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.PolicyEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.DatasetRepository;
import zw.gov.mohcc.impilo.datagovernance.repository.DecisionAuditRepository;
import zw.gov.mohcc.impilo.datagovernance.repository.GovernanceRuleRepository;
import zw.gov.mohcc.impilo.datagovernance.repository.GrantRepository;
import zw.gov.mohcc.impilo.datagovernance.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.datagovernance.repository.PolicyRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class GovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceService.class);
    private static final String PRODUCER = "data-governance-service";
    private static final String POLICY_VERSION = "dgv-v1.0";
    private static final int DEFAULT_TTL_SECONDS = 60;
    private static final Set<String> VALID_PURPOSES = Set.of(
            "TREATMENT", "PUBLIC_HEALTH", "RESEARCH", "ADMIN");

    private final DatasetRepository datasetRepository;
    private final GrantRepository grantRepository;
    private final DecisionAuditRepository decisionAuditRepository;
    private final OutboxEventRepository outboxRepository;
    private final GovernanceRuleRepository ruleRepository;
    private final PolicyRepository policyRepository;
    private final ObjectMapper objectMapper;

    public GovernanceService(DatasetRepository datasetRepository,
                             GrantRepository grantRepository,
                             DecisionAuditRepository decisionAuditRepository,
                             OutboxEventRepository outboxRepository,
                             GovernanceRuleRepository ruleRepository,
                             PolicyRepository policyRepository,
                             ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.grantRepository = grantRepository;
        this.decisionAuditRepository = decisionAuditRepository;
        this.outboxRepository = outboxRepository;
        this.ruleRepository = ruleRepository;
        this.policyRepository = policyRepository;
        this.objectMapper = objectMapper;
    }

    // ── Dataset registration ──

    @Transactional
    public DatasetEntity createDataset(CreateDatasetRequest request, UUID tenantId,
                                        String podId, String correlationId,
                                        String idempotencyKey) {
        DatasetEntity dataset = new DatasetEntity(
                tenantId, request.name(), request.classification(), request.description());
        datasetRepository.save(dataset);

        Map<String, Object> payload = buildDeltaPayload("CREATE", null,
                Map.of("dataset_id", dataset.getDatasetId().toString(),
                        "name", dataset.getName(),
                        "classification", dataset.getClassification()),
                List.of("name", "classification", "description"));

        appendOutboxEvent("impilo.data.governance.dataset.created.v1",
                "Dataset", dataset.getDatasetId().toString(),
                tenantId, podId, correlationId, idempotencyKey,
                payload, dataset.getDatasetId().toString(),
                dataset.getDatasetId().toString(), "Dataset");

        log.info("Created dataset [datasetId={}, name={}]", dataset.getDatasetId(), dataset.getName());
        return dataset;
    }

    @Transactional(readOnly = true)
    public List<DatasetEntity> listDatasets(UUID tenantId) {
        return datasetRepository.findByTenantId(tenantId);
    }

    // ── Grant creation ──

    @Transactional
    public GrantEntity createGrant(CreateGrantRequest request, UUID tenantId,
                                    String podId, String correlationId,
                                    String idempotencyKey) {
        OffsetDateTime expiresAt = request.expiresAt() != null
                ? OffsetDateTime.parse(request.expiresAt())
                : null;

        GrantEntity grant = new GrantEntity(
                tenantId, request.datasetId(), request.principalId(),
                request.purposeOfUse(), expiresAt);
        grantRepository.save(grant);

        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("grant_id", grant.getGrantId().toString());
        afterState.put("dataset_id", grant.getDatasetId().toString());
        afterState.put("principal_id", grant.getPrincipalId());
        afterState.put("purpose_of_use", grant.getPurposeOfUse());
        if (expiresAt != null) {
            afterState.put("expires_at", expiresAt.toString());
        }

        Map<String, Object> payload = buildDeltaPayload("CREATE", null, afterState,
                List.of("dataset_id", "principal_id", "purpose_of_use", "expires_at"));

        appendOutboxEvent("impilo.data.governance.grant.created.v1",
                "Grant", grant.getGrantId().toString(),
                tenantId, podId, correlationId, idempotencyKey,
                payload, grant.getPrincipalId(),
                grant.getGrantId().toString(), "Grant");

        log.info("Created grant [grantId={}, principal={}, dataset={}, purpose={}]",
                grant.getGrantId(), grant.getPrincipalId(),
                grant.getDatasetId(), grant.getPurposeOfUse());
        return grant;
    }

    @Transactional
    public GrantEntity revokeGrant(RevokeGrantRequest request, UUID tenantId,
                                    String podId, String correlationId,
                                    String idempotencyKey) {
        GrantEntity grant = grantRepository.findById(request.grantId())
                .filter(g -> g.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Grant not found for tenant"));

        if (!grant.isRevoked()) {
            grant.setRevoked(true);
            grant.setRevokedAt(OffsetDateTime.now());
            grant.setRevokedBy(request.revokedBy());
            grantRepository.save(grant);

            Map<String, Object> afterState = new LinkedHashMap<>();
            afterState.put("grant_id", grant.getGrantId().toString());
            afterState.put("revoked", true);
            afterState.put("revoked_at", grant.getRevokedAt().toString());
            afterState.put("revoked_by", grant.getRevokedBy());

            Map<String, Object> payload = buildDeltaPayload("UPDATE", null, afterState,
                    List.of("revoked", "revoked_at", "revoked_by"));

            appendOutboxEvent("impilo.data.governance.grant.revoked.v1",
                    "Grant", grant.getGrantId().toString(),
                    tenantId, podId, correlationId, idempotencyKey,
                    payload, grant.getPrincipalId(),
                    grant.getGrantId().toString(), "Grant");
        }

        log.info("Revoked grant [grantId={}]", grant.getGrantId());
        return grant;
    }

    @Transactional
    public PolicyEntity publishPolicy(PublishPolicyRequest request, UUID tenantId,
                                       String podId, String correlationId,
                                       String idempotencyKey) {
        int nextVersion = policyRepository.findMaxVersion(tenantId, request.name()).orElse(0) + 1;
        PolicyEntity policy = new PolicyEntity(
                tenantId, request.name(), nextVersion, request.description(), request.rulesJson());
        policy.setStatus("PUBLISHED");
        policy.setPublishedAt(OffsetDateTime.now());
        policyRepository.save(policy);

        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("policy_id", policy.getPolicyId().toString());
        afterState.put("name", policy.getName());
        afterState.put("version", policy.getVersion());
        afterState.put("status", policy.getStatus());

        Map<String, Object> payload = buildDeltaPayload("CREATE", null, afterState,
                List.of("policy_id", "name", "version", "rules_json", "status"));

        appendOutboxEvent("impilo.data.governance.policy.published.v1",
                "Policy", policy.getPolicyId().toString(),
                tenantId, podId, correlationId, idempotencyKey,
                payload, policy.getPolicyId().toString(),
                policy.getPolicyId().toString(), "Policy");

        log.info("Published policy [policyId={}, name={}, version={}]",
                policy.getPolicyId(), policy.getName(), policy.getVersion());
        return policy;
    }

    // ── Access decision ──

    @Transactional
    public DecideResponse decide(DecideRequest request, UUID tenantId,
                                  String correlationId) {
        List<String> reasonCodes = new ArrayList<>();
        String decision;

        // Look up dataset by name
        Optional<DatasetEntity> datasetOpt = datasetRepository
                .findByTenantIdAndName(tenantId, request.dataset());

        if (datasetOpt.isEmpty()) {
            decision = "DENY";
            reasonCodes.add("DATASET_NOT_FOUND");
        } else {
            DatasetEntity dataset = datasetOpt.get();

            // Validate purpose_of_use
            if (!VALID_PURPOSES.contains(request.purposeOfUse())) {
                decision = "DENY";
                reasonCodes.add("INVALID_PURPOSE_OF_USE");
            } else {
                // Check for active grant
                Optional<GrantEntity> grantOpt = grantRepository.findActiveGrant(
                        tenantId, dataset.getDatasetId(),
                        request.principalId(), request.purposeOfUse(),
                        OffsetDateTime.now());

                if (grantOpt.isPresent()) {
                    decision = "ALLOW";
                    reasonCodes.add("GRANT_ACTIVE");
                } else {
                    decision = "DENY";
                    reasonCodes.add("NO_ACTIVE_GRANT");
                }
            }
        }

        // Always audit the decision
        DecisionAuditEntity audit = new DecisionAuditEntity();
        audit.setTenantId(tenantId);
        audit.setDatasetId(datasetOpt.map(DatasetEntity::getDatasetId).orElse(null));
        audit.setDatasetName(request.dataset());
        audit.setPrincipalId(request.principalId());
        audit.setDecision(decision);
        audit.setReasonCodesJson(toJson(reasonCodes));
        audit.setPolicyVersion(POLICY_VERSION);
        audit.setQueryFingerprint(request.queryFingerprint());
        audit.setCorrelationId(correlationId);
        decisionAuditRepository.save(audit);

        // Outbox event for decision logging — use a unique idempotency key
        String decisionIdempotencyKey = "decide-" + audit.getDecisionId();
        Map<String, Object> decisionPayload = new LinkedHashMap<>();
        decisionPayload.put("decision_id", audit.getDecisionId().toString());
        decisionPayload.put("dataset", request.dataset());
        decisionPayload.put("principal_id", request.principalId());
        decisionPayload.put("decision", decision);
        decisionPayload.put("reason_codes", reasonCodes);
        decisionPayload.put("policy_version", POLICY_VERSION);

        appendOutboxEvent("impilo.data.governance.decision.logged.v1",
                "DecisionAudit", audit.getDecisionId().toString(),
                tenantId, null, correlationId, decisionIdempotencyKey,
                decisionPayload, request.principalId(),
                request.principalId(), "DecisionAudit");

        log.info("Decision [principal={}, dataset={}, purpose={}, decision={}]",
                request.principalId(), request.dataset(),
                request.purposeOfUse(), decision);

        return new DecideResponse(decision, POLICY_VERSION, reasonCodes, DEFAULT_TTL_SECONDS);
    }

    // ── Export evaluation ──

    @Transactional
    public ExportResponse evaluateExport(ExportRequest request, UUID tenantId,
                                          String correlationId, String idempotencyKey) {
        // Find matching rules for this dataset + EXPORT action
        List<GovernanceRuleEntity> rules = ruleRepository.findMatchingRules(
                tenantId, request.dataset(), "EXPORT");

        String decision = "DENY";  // deny by default
        String message = "No explicit ALLOW rule found for this export";

        for (GovernanceRuleEntity rule : rules) {
            if ("ALLOW".equals(rule.getEffect())) {
                // Check purpose matches if rule requires specific purpose
                if (rule.getRequiredPurpose() == null ||
                    rule.getRequiredPurpose().equals(request.purposeOfUse())) {
                    decision = "ALLOW";
                    message = "Export permitted by rule: " + rule.getName();
                    break;
                }
            } else if ("DENY".equals(rule.getEffect())) {
                decision = "DENY";
                message = "Export denied by rule: " + rule.getName();
                break;
            }
        }

        String exportId = UUID.randomUUID().toString();

        // Audit the export decision
        DecisionAuditEntity audit = new DecisionAuditEntity();
        audit.setTenantId(tenantId);
        audit.setDatasetName(request.dataset());
        audit.setPrincipalId("export-requester");
        audit.setDecision(decision);
        audit.setReasonCodesJson(toJson(List.of(decision.equals("ALLOW") ? "EXPORT_RULE_MATCH" : "NO_EXPORT_RULE")));
        audit.setPolicyVersion(POLICY_VERSION);
        audit.setCorrelationId(correlationId);
        decisionAuditRepository.save(audit);

        // Outbox event
        String decisionIdempotencyKey = idempotencyKey != null ? idempotencyKey : "export-" + exportId;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("export_id", exportId);
        payload.put("dataset", request.dataset());
        payload.put("purpose_of_use", request.purposeOfUse());
        payload.put("decision", decision);

        appendOutboxEvent("impilo.data.governance.export.evaluated.v1",
                "Export", exportId,
                tenantId, null, correlationId, decisionIdempotencyKey,
                payload, "export-requester",
                exportId, "Export");

        return new ExportResponse(decision, request.dataset(), request.purposeOfUse(), exportId, message);
    }

    // ── Outbox helper ──

    private void appendOutboxEvent(String eventType, String aggregateType, String aggregateId,
                                    UUID tenantId, String podId, String correlationId,
                                    String idempotencyKey, Map<String, Object> payload,
                                    String partitionKey, String subjectId, String subjectType) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(subjectId);
        outbox.setSubjectType(subjectType);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private Map<String, Object> buildDeltaPayload(String op, Map<String, Object> before,
                                                    Map<String, Object> after,
                                                    List<String> changedFields) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("op", op);
        delta.put("before", before);
        delta.put("after", after);
        delta.put("changed_fields", changedFields);
        return delta;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
