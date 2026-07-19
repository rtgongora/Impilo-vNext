package zw.gov.mohcc.impilo.datagovernance.deid.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.datagovernance.deid.api.dto.CreateDeidDatasetRequest;
import zw.gov.mohcc.impilo.datagovernance.deid.api.dto.RequestDeidRunRequest;
import zw.gov.mohcc.impilo.datagovernance.deid.api.dto.UpsertReleasePolicyRequest;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidDatasetEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidReleaseManifestEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidReleasePolicyEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidRunEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidRunStatus;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidZone;
import zw.gov.mohcc.impilo.datagovernance.deid.repository.DeidDatasetRepository;
import zw.gov.mohcc.impilo.datagovernance.deid.repository.DeidReleaseManifestRepository;
import zw.gov.mohcc.impilo.datagovernance.deid.repository.DeidReleasePolicyRepository;
import zw.gov.mohcc.impilo.datagovernance.deid.repository.DeidRunRepository;
import zw.gov.mohcc.impilo.datagovernance.deid.transform.DataUsePolicyGate;
import zw.gov.mohcc.impilo.datagovernance.deid.transform.DatasetTokeniser;
import zw.gov.mohcc.impilo.datagovernance.deid.transform.DirectIdentifierStripper;
import zw.gov.mohcc.impilo.datagovernance.deid.transform.GeneralisationRules;
import zw.gov.mohcc.impilo.datagovernance.deid.transform.KAnonymitySuppressor;
import zw.gov.mohcc.impilo.datagovernance.deid.transform.QuasiIdentifierGeneraliser;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * De-identification release orchestration
 * (design: docs/data-platform/de-identification-pipeline.md).
 *
 * <p>Drives the {@link DeidRunStatus} FSM: request → tokenise + strip
 * (TOKENISED zone) → generalise (GENERALISED zone) → k-anonymity QA →
 * data-use policy gate → {@code RELEASED} (manifest row + outbox event) or
 * {@code REJECTED} (reason recorded + outbox event). Every state change is
 * audited through the transactional outbox; payloads never carry row values.</p>
 */
@Service
public class DeidRunService {

    private static final Logger log = LoggerFactory.getLogger(DeidRunService.class);

    static final Set<String> VALID_PURPOSES = Set.of("RESEARCH", "PUBLIC_HEALTH");
    static final Set<String> VALID_POLICY_STATUSES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "REVOKED");

    static final String REASON_DATASET_NOT_ACTIVE = "DATASET_NOT_ACTIVE";
    static final String REASON_MISSING_SUBJECT_KEY = "MISSING_SUBJECT_KEY";
    static final String REASON_SECRET_UNAVAILABLE = "SECRET_UNAVAILABLE";
    static final String REASON_PII_LEAK_DETECTED = "PII_LEAK_DETECTED";
    static final String REASON_TRANSFORM_FAILED = "TRANSFORM_FAILED";

    private final DeidDatasetRepository datasetRepository;
    private final DeidRunRepository runRepository;
    private final DeidReleasePolicyRepository policyRepository;
    private final DeidReleaseManifestRepository manifestRepository;
    private final DatasetTokeniser tokeniser;
    private final DirectIdentifierStripper stripper;
    private final QuasiIdentifierGeneraliser generaliser;
    private final KAnonymitySuppressor suppressor;
    private final DataUsePolicyGate policyGate;
    private final DeidOutboxAppender outboxAppender;
    private final ObjectMapper objectMapper;

    public DeidRunService(DeidDatasetRepository datasetRepository,
                          DeidRunRepository runRepository,
                          DeidReleasePolicyRepository policyRepository,
                          DeidReleaseManifestRepository manifestRepository,
                          DatasetTokeniser tokeniser,
                          DirectIdentifierStripper stripper,
                          QuasiIdentifierGeneraliser generaliser,
                          KAnonymitySuppressor suppressor,
                          DataUsePolicyGate policyGate,
                          DeidOutboxAppender outboxAppender,
                          ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.runRepository = runRepository;
        this.policyRepository = policyRepository;
        this.manifestRepository = manifestRepository;
        this.tokeniser = tokeniser;
        this.stripper = stripper;
        this.generaliser = generaliser;
        this.suppressor = suppressor;
        this.policyGate = policyGate;
        this.outboxAppender = outboxAppender;
        this.objectMapper = objectMapper;
    }

    /** Outcome of a run request; {@code releasedRows} is empty unless RELEASED. */
    public record RunOutcome(DeidRunEntity run, List<Map<String, Object>> releasedRows, UUID manifestId) {}

    // ── Dataset registration ──

    @Transactional
    public DeidDatasetEntity createDataset(CreateDeidDatasetRequest request, UUID tenantId,
                                           String podId, String correlationId,
                                           String idempotencyKey) {
        String purpose = request.purpose().toUpperCase(Locale.ROOT);
        if (!VALID_PURPOSES.contains(purpose)) {
            throw new IllegalArgumentException(
                    "Invalid dataset purpose (expected one of " + VALID_PURPOSES + ")");
        }
        DeidZone zoneTarget = parseZoneTarget(request.zoneTarget());
        datasetRepository.findByTenantIdAndDatasetCode(tenantId, request.datasetCode())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Dataset code already registered: " + request.datasetCode());
                });

        DeidDatasetEntity dataset = new DeidDatasetEntity(
                tenantId, request.datasetCode(), request.name(), purpose,
                zoneTarget, request.policyRef(), request.secretRef());
        datasetRepository.save(dataset);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataset_id", dataset.getDatasetId().toString());
        payload.put("dataset_code", dataset.getDatasetCode());
        payload.put("purpose", dataset.getPurpose());
        payload.put("zone_target", dataset.getZoneTarget().name());
        payload.put("policy_ref", dataset.getPolicyRef());
        payload.put("secret_ref", dataset.getSecretRef());

        outboxAppender.append("impilo.data.governance.deid.dataset.registered.v1",
                "DeidDataset", dataset.getDatasetId().toString(),
                tenantId, podId, correlationId, idempotencyKey,
                payload, dataset.getDatasetId().toString(),
                dataset.getDatasetId().toString(), "DeidDataset");

        log.info("Registered deid dataset [code={}, purpose={}, zone={}]",
                dataset.getDatasetCode(), dataset.getPurpose(), dataset.getZoneTarget());
        return dataset;
    }

    @Transactional(readOnly = true)
    public List<DeidDatasetEntity> listDatasets(UUID tenantId) {
        return datasetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    // ── Release policy upsert ──

    @Transactional
    public DeidReleasePolicyEntity upsertPolicy(UpsertReleasePolicyRequest request, UUID tenantId,
                                                String podId, String correlationId,
                                                String idempotencyKey) {
        String purpose = request.purpose().toUpperCase(Locale.ROOT);
        if (!VALID_PURPOSES.contains(purpose)) {
            throw new IllegalArgumentException(
                    "Invalid policy purpose (expected one of " + VALID_PURPOSES + ")");
        }
        if (request.status() != null
                && !VALID_POLICY_STATUSES.contains(request.status().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Invalid policy status (expected one of " + VALID_POLICY_STATUSES + ")");
        }
        if (request.kThreshold() != null && request.kThreshold() < 2) {
            throw new IllegalArgumentException("k_threshold must be >= 2");
        }

        DeidReleasePolicyEntity policy = policyRepository
                .findByTenantIdAndPolicyCode(tenantId, request.policyCode())
                .map(existing -> {
                    existing.setVersion(existing.getVersion() + 1);
                    existing.setUpdatedAt(OffsetDateTime.now());
                    return existing;
                })
                .orElseGet(() -> new DeidReleasePolicyEntity(tenantId, request.policyCode(), purpose));

        policy.setPurpose(purpose);
        if (request.requester() != null) policy.setRequester(request.requester());
        if (request.retentionDays() != null) policy.setRetentionDays(request.retentionDays());
        if (request.reidProhibited() != null) policy.setReidProhibited(request.reidProhibited());
        if (request.kThreshold() != null) policy.setKThreshold(request.kThreshold());
        if (request.quasiIdentifierRules() != null) {
            policy.setQuasiIdentifierRulesJson(request.quasiIdentifierRules().toString());
        }
        if (request.allowlistColumns() != null) {
            policy.setAllowlistColumnsJson(toJson(request.allowlistColumns()));
        }
        if (request.status() != null) {
            policy.setStatus(request.status().toUpperCase(Locale.ROOT));
        }
        policyRepository.save(policy);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policy_id", policy.getPolicyId().toString());
        payload.put("policy_code", policy.getPolicyCode());
        payload.put("purpose", policy.getPurpose());
        payload.put("status", policy.getStatus());
        payload.put("k_threshold", policy.getKThreshold());
        payload.put("version", policy.getVersion());

        outboxAppender.append("impilo.data.governance.deid.policy.upserted.v1",
                "DeidReleasePolicy", policy.getPolicyId().toString(),
                tenantId, podId, correlationId, idempotencyKey,
                payload, policy.getPolicyId().toString(),
                policy.getPolicyId().toString(), "DeidReleasePolicy");

        log.info("Upserted deid release policy [code={}, status={}, k={}, version={}]",
                policy.getPolicyCode(), policy.getStatus(), policy.getKThreshold(), policy.getVersion());
        return policy;
    }

    // ── Run orchestration (FSM) ──

    @Transactional
    public RunOutcome requestRun(RequestDeidRunRequest request, UUID tenantId,
                                 String podId, String correlationId,
                                 String idempotencyKey) {
        DeidDatasetEntity dataset = datasetRepository
                .findByTenantIdAndDatasetCode(tenantId, request.datasetCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown de-identification dataset for tenant: " + request.datasetCode()));

        List<Map<String, Object>> sourceRows = request.sourceRows();
        DeidRunEntity run = new DeidRunEntity(tenantId, dataset.getDatasetId(),
                request.requestedBy(), correlationId);
        run.setRowsIn(sourceRows.size());
        runRepository.save(run);

        appendRunEvent(run, dataset, "impilo.data.governance.deid.run.requested.v1",
                podId, Map.of("rows_in", sourceRows.size(), "requested_by", request.requestedBy()));

        if (!"ACTIVE".equals(dataset.getStatus())) {
            return reject(run, dataset, podId, REASON_DATASET_NOT_ACTIVE,
                    "Dataset status is " + dataset.getStatus());
        }

        DeidReleasePolicyEntity configPolicy = resolveConfigPolicy(dataset);
        Set<String> allowlist = parseAllowlist(configPolicy);
        GeneralisationRules rules = configPolicy != null
                ? GeneralisationRules.fromJson(objectMapper, configPolicy.getQuasiIdentifierRulesJson())
                : GeneralisationRules.empty();
        int k = configPolicy != null ? configPolicy.getKThreshold() : KAnonymitySuppressor.DEFAULT_K;

        // ── TRANSFORMING: tokenise + strip (TOKENISED zone), generalise (GENERALISED zone) ──
        transition(run, dataset, DeidRunStatus.TRANSFORMING, podId, null);
        run.setStartedAt(OffsetDateTime.now());

        List<Map<String, Object>> transformed;
        Set<String> sourceCpids = new HashSet<>();
        try {
            transformed = new ArrayList<>(sourceRows.size());
            for (Map<String, Object> row : sourceRows) {
                String cpid = extractCpid(row);
                sourceCpids.add(cpid);
                String token = tokeniser.subjectToken(dataset.getSecretRef(), cpid);
                Map<String, Object> stripped = stripper.strip(row, allowlist);
                stripped.put(QuasiIdentifierGeneraliser.SUBJECT_TOKEN_COLUMN, token);
                transformed.add(stripped);
            }
            if (dataset.getZoneTarget().atLeast(DeidZone.GENERALISED)) {
                transformed = generaliser.generalise(transformed, rules, LocalDate.now());
            }
            assertNoDirectIdentifiers(transformed, sourceCpids);
        } catch (PiiLeakException e) {
            return reject(run, dataset, podId, REASON_PII_LEAK_DETECTED, e.getMessage());
        } catch (IllegalStateException e) {
            return reject(run, dataset, podId, REASON_SECRET_UNAVAILABLE, e.getMessage());
        } catch (java.time.format.DateTimeParseException e) {
            return reject(run, dataset, podId, REASON_TRANSFORM_FAILED, e.getMessage());
        } catch (IllegalArgumentException e) {
            return reject(run, dataset, podId, REASON_MISSING_SUBJECT_KEY, e.getMessage());
        }

        // ── QA: k-anonymity suppression + report ──
        transition(run, dataset, DeidRunStatus.QA, podId, null);
        KAnonymitySuppressor.SuppressionResult qa =
                suppressor.suppress(transformed, rules.quasiColumns(), k);

        Map<String, Object> qaReport = new LinkedHashMap<>();
        qaReport.put("k_threshold", k);
        qaReport.put("quasi_columns", rules.quasiColumns());
        qaReport.put("rows_in", sourceRows.size());
        qaReport.put("rows_out", qa.retainedRows().size());
        qaReport.put("rows_suppressed", qa.suppressedRowCount());
        qaReport.put("equivalence_classes", qa.equivalenceClassCount());
        qaReport.put("suppressed_classes", qa.suppressedClassCount());
        qaReport.put("min_retained_class_size", qa.minRetainedClassSize());
        run.setQaReportJson(toJson(qaReport));
        run.setRowsOut(qa.retainedRows().size());
        run.setRowsSuppressed(qa.suppressedRowCount());

        // ── Policy gate: deny by default ──
        DataUsePolicyGate.GateDecision decision =
                policyGate.evaluate(dataset, policyRepository.findByTenantId(tenantId));
        if (!decision.allowed()) {
            return reject(run, dataset, podId, decision.reasonCode(),
                    "No active data-use release policy authorises this dataset");
        }

        DeidReleasePolicyEntity policy = decision.policy();
        run.setPolicyId(policy.getPolicyId());
        run.setPolicyVersion(String.valueOf(policy.getVersion()));

        // ── RELEASED: manifest row + outbox event ──
        DeidReleaseManifestEntity manifest = new DeidReleaseManifestEntity(
                tenantId, run.getRunId(), dataset.getDatasetId(), dataset.getDatasetCode(),
                dataset.getZoneTarget(), qa.retainedRows().size(), k,
                policy.getPolicyId(), String.valueOf(policy.getVersion()));
        manifest.setColumnsJson(toJson(releasedColumns(qa.retainedRows())));
        manifest.setSuppressionStatsJson(toJson(qaReport));
        manifestRepository.save(manifest);

        run.setCompletedAt(OffsetDateTime.now());
        transition(run, dataset, DeidRunStatus.RELEASED, podId, Map.of(
                "manifest_id", manifest.getManifestId().toString(),
                "rows_out", qa.retainedRows().size(),
                "rows_suppressed", qa.suppressedRowCount(),
                "policy_id", policy.getPolicyId().toString(),
                "policy_version", policy.getVersion()));

        log.info("Deid run RELEASED [runId={}, dataset={}, rowsIn={}, rowsOut={}, suppressed={}]",
                run.getRunId(), dataset.getDatasetCode(), run.getRowsIn(),
                run.getRowsOut(), run.getRowsSuppressed());
        return new RunOutcome(run, qa.retainedRows(), manifest.getManifestId());
    }

    @Transactional(readOnly = true)
    public Optional<DeidRunEntity> getRun(UUID runId, UUID tenantId) {
        return runRepository.findByRunIdAndTenantId(runId, tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<DeidReleaseManifestEntity> getManifest(UUID runId) {
        return manifestRepository.findByRunId(runId);
    }

    @Transactional(readOnly = true)
    public Optional<DeidDatasetEntity> getDataset(UUID datasetId) {
        return datasetRepository.findById(datasetId);
    }

    // ── FSM internals ──

    private RunOutcome reject(DeidRunEntity run, DeidDatasetEntity dataset, String podId,
                              String reasonCode, String detail) {
        run.setRejectionReason(detail != null ? reasonCode + ": " + detail : reasonCode);
        run.setCompletedAt(OffsetDateTime.now());
        transition(run, dataset, DeidRunStatus.REJECTED, podId,
                Map.of("reason_code", reasonCode, "reason", run.getRejectionReason()));
        log.warn("Deid run REJECTED [runId={}, dataset={}, reason={}]",
                run.getRunId(), dataset.getDatasetCode(), run.getRejectionReason());
        return new RunOutcome(run, List.of(), null);
    }

    private void transition(DeidRunEntity run, DeidDatasetEntity dataset,
                            DeidRunStatus target, String podId,
                            Map<String, Object> extraPayload) {
        DeidRunStatus from = run.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal deid run transition " + from + " -> " + target);
        }
        run.setStatus(target);
        run.setUpdatedAt(OffsetDateTime.now());
        runRepository.save(run);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from_status", from.name());
        payload.put("to_status", target.name());
        if (extraPayload != null) {
            payload.putAll(extraPayload);
        }
        String eventType = switch (target) {
            case RELEASED -> "impilo.data.governance.deid.run.released.v1";
            case REJECTED -> "impilo.data.governance.deid.run.rejected.v1";
            default -> "impilo.data.governance.deid.run.state.changed.v1";
        };
        appendRunEvent(run, dataset, eventType, podId, payload);
    }

    private void appendRunEvent(DeidRunEntity run, DeidDatasetEntity dataset,
                                String eventType, String podId, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", run.getRunId().toString());
        payload.put("dataset_id", dataset.getDatasetId().toString());
        payload.put("dataset_code", dataset.getDatasetCode());
        payload.put("status", run.getStatus().name());
        if (extra != null) {
            payload.putAll(extra);
        }
        String statusMarker = eventType.endsWith("requested.v1")
                ? "requested"
                : run.getStatus().name().toLowerCase(Locale.ROOT);
        outboxAppender.append(eventType,
                "DeidRun", run.getRunId().toString(),
                run.getTenantId(), podId, run.getCorrelationId(),
                "deid-run-" + run.getRunId() + "-" + statusMarker,
                payload, dataset.getDatasetId().toString(),
                run.getRunId().toString(), "DeidRun");
    }

    // ── Transform plumbing ──

    private DeidReleasePolicyEntity resolveConfigPolicy(DeidDatasetEntity dataset) {
        if (dataset.getPolicyRef() != null) {
            return policyRepository
                    .findByTenantIdAndPolicyCode(dataset.getTenantId(), dataset.getPolicyRef())
                    .orElse(null);
        }
        return policyRepository.findByTenantId(dataset.getTenantId()).stream()
                .filter(p -> p.getPurpose().equals(dataset.getPurpose()))
                .filter(DeidReleasePolicyEntity::isActive)
                .findFirst()
                .orElse(null);
    }

    private Set<String> parseAllowlist(DeidReleasePolicyEntity policy) {
        if (policy == null || policy.getAllowlistColumnsJson() == null) {
            return Set.of();    // no policy => nothing allowlisted => everything dropped
        }
        try {
            List<String> columns = objectMapper.readValue(policy.getAllowlistColumnsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return new LinkedHashSet<>(columns);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid allowlist_columns JSON", e);
        }
    }

    private static String extractCpid(Map<String, Object> row) {
        Object value = row.get("cpid");
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if ("cpid".equalsIgnoreCase(entry.getKey())) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Every source row must carry a cpid subject key");
        }
        return String.valueOf(value);
    }

    /** Defence in depth: no identifier-named column and no raw CPID value may survive the transforms. */
    private void assertNoDirectIdentifiers(List<Map<String, Object>> rows, Set<String> sourceCpids) {
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (stripper.isDirectIdentifier(entry.getKey())) {
                    throw new PiiLeakException("Identifier-named column survived transforms: " + entry.getKey());
                }
                if (entry.getValue() != null && sourceCpids.contains(String.valueOf(entry.getValue()))) {
                    throw new PiiLeakException("Raw CPID value survived transforms in column: " + entry.getKey());
                }
            }
        }
    }

    private static List<String> releasedColumns(List<Map<String, Object>> rows) {
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            columns.addAll(row.keySet());
        }
        return new ArrayList<>(columns);
    }

    private DeidZone parseZoneTarget(String zoneTarget) {
        DeidZone zone = DeidZone.RELEASED;
        if (zoneTarget != null && !zoneTarget.isBlank()) {
            try {
                zone = DeidZone.valueOf(zoneTarget.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown zone_target: " + zoneTarget);
            }
        }
        if (!zone.isMaterialisationTarget()) {
            throw new IllegalArgumentException(
                    "RAW is source-only and can never be a materialisation target");
        }
        return zone;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /** Raised when the defence-in-depth leak check trips; the run is rejected, never errored. */
    private static final class PiiLeakException extends RuntimeException {
        PiiLeakException(String message) {
            super(message);
        }
    }
}
