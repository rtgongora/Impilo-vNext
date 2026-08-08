package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.JobStatus;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.domain.ScopeType;
import zw.gov.mohcc.impilo.zibo.domain.ValidationOutcome;
import zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.AssignmentEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationJobEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationLogEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.AssignmentRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationJobRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationLogRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Core validation engine for terminology compliance checking.
 *
 * <p>Validates FHIR codings against known CodeSystem artifacts and enforces
 * policy modes (STRICT vs LENIENT) at different scope levels. In STRICT mode,
 * unknown codes produce ERROR-severity issues; in LENIENT mode they produce
 * WARNING-severity issues.</p>
 *
 * <p>Policy mode resolution follows a precedence hierarchy:
 * explicit override > workspace assignment > facility assignment > tenant
 * assignment > global default (from configuration).</p>
 *
 * <p>Supports both synchronous validation (single coding or resource) and
 * asynchronous batch validation via the job queue.</p>
 */
@Service
public class ValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(ValidationEngine.class);

    private final ArtifactRepository artifactRepository;
    private final AssignmentRepository assignmentRepository;
    private final ValidationJobRepository validationJobRepository;
    private final ValidationLogRepository validationLogRepository;
    private final EventOutboxRepository outboxRepository;
    private final ZiboProperties ziboProperties;
    private final ObjectMapper objectMapper;
    private final ArtifactResolutionService artifactResolutionService;
    private final MeterRegistry meterRegistry;

    public ValidationEngine(ArtifactRepository artifactRepository,
                            AssignmentRepository assignmentRepository,
                            ValidationJobRepository validationJobRepository,
                            ValidationLogRepository validationLogRepository,
                            EventOutboxRepository outboxRepository,
                            ZiboProperties ziboProperties,
                            ObjectMapper objectMapper,
                            ArtifactResolutionService artifactResolutionService,
                            MeterRegistry meterRegistry) {
        this.artifactRepository = artifactRepository;
        this.assignmentRepository = assignmentRepository;
        this.validationJobRepository = validationJobRepository;
        this.validationLogRepository = validationLogRepository;
        this.outboxRepository = outboxRepository;
        this.ziboProperties = ziboProperties;
        this.objectMapper = objectMapper;
        this.artifactResolutionService = artifactResolutionService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Validate a single FHIR coding against a registered CodeSystem artifact.
     *
     * <p>Looks up the CodeSystem by its canonical URL (the system parameter),
     * parses its content JSON, and searches for the code. The effective policy
     * mode determines the severity of issues for unknown codes.</p>
     *
     * @param system       the coding system URI (canonical URL of the CodeSystem)
     * @param code         the code to validate
     * @param display      the display text to validate (informational; not currently checked)
     * @param modeOverride explicit policy mode override; may be {@code null}
     * @param facilityId   the facility context for policy resolution; may be {@code null}
     * @return a validation result containing validity status and any issues
     */
    @Transactional
    public ValidationResult validateCoding(String system, String code, String display,
                                           PolicyMode modeOverride, UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        PolicyMode effectiveMode = resolveEffectiveMode(tenantId, facilityId, modeOverride);

        // Resolution is delegated: highest version by version_sort_key (never by clock), effective
        // window honoured, and national terminology reachable from either tenant plane. All three
        // were wrong here — see ArtifactResolutionService.
        Optional<ArtifactResolutionService.Resolved> resolvedOpt =
                artifactResolutionService.resolveCurrent(tenantId, system, ArtifactType.CODE_SYSTEM);

        if (resolvedOpt.isEmpty()) {
            // No CodeSystem found for this system
            ValidationSeverity severity = effectiveMode == PolicyMode.STRICT
                    ? ValidationSeverity.ERROR : ValidationSeverity.WARNING;

            ValidationIssue issue = new ValidationIssue(
                    severity,
                    "not-found",
                    "CodeSystem not found for system: " + system,
                    system);

            logOutcome(tenantId, facilityId, severity, "not-found", system, null,
                    "CodeSystem not found for system: " + system,
                    system, code, ValidationOutcome.UNKNOWN_SYSTEM, effectiveMode);

            boolean valid = severity != ValidationSeverity.ERROR;
            return new ValidationResult(valid, List.of(issue));
        }

        ArtifactEntity codeSystem = resolvedOpt.get().artifact();
        boolean codeFound = lookupCodeInContent(codeSystem.getContentJson(), code);

        if (codeFound) {
            // The denominator. Recording only failures is why nobody could state how coded this
            // estate is: a hundred failures might be a catastrophe or a rounding error.
            logOutcome(tenantId, facilityId, ValidationSeverity.INFORMATION, "resolved",
                    system, codeSystem.getVersion(), null,
                    system, code, ValidationOutcome.RESOLVED, effectiveMode);
            return new ValidationResult(true, Collections.emptyList());
        }

        // Code not found
        ValidationSeverity severity = effectiveMode == PolicyMode.STRICT
                ? ValidationSeverity.ERROR : ValidationSeverity.WARNING;

        ValidationIssue issue = new ValidationIssue(
                severity,
                "code-invalid",
                "Code '" + code + "' not found in CodeSystem " + system
                        + " (version " + codeSystem.getVersion() + ")",
                system);

        logOutcome(tenantId, facilityId, severity, "code-invalid",
                system, codeSystem.getVersion(),
                "Code '" + code + "' not found in CodeSystem",
                system, code, ValidationOutcome.UNKNOWN_CODE, effectiveMode);

        boolean valid = severity != ValidationSeverity.ERROR;
        return new ValidationResult(valid, List.of(issue));
    }

    /**
     * Validate all codings within a FHIR resource JSON document.
     *
     * <p>Parses the JSON, extracts all {@code coding} arrays (including nested
     * within {@code code}, {@code valueCodeableConcept}, {@code category}, etc.),
     * and validates each coding individually.</p>
     *
     * @param resourceJson the FHIR resource JSON string
     * @param modeOverride explicit policy mode override; may be {@code null}
     * @param facilityId   the facility context for policy resolution; may be {@code null}
     * @return a list of validation issues found across all codings
     */
    @Transactional
    public List<ValidationIssue> validateResource(String resourceJson, PolicyMode modeOverride, UUID facilityId) {
        List<ValidationIssue> allIssues = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(resourceJson);
            List<CodingEntry> codings = extractCodings(root);

            for (CodingEntry coding : codings) {
                ValidationResult result = validateCoding(
                        coding.system, coding.code, coding.display, modeOverride, facilityId);
                allIssues.addAll(result.issues());
            }
        } catch (JsonProcessingException e) {
            allIssues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "invalid-json",
                    "Failed to parse resource JSON: " + e.getMessage(),
                    null));
        }

        return allIssues;
    }

    /**
     * Submit a validation job for asynchronous processing.
     *
     * <p>The job is stored with PENDING status and will be picked up by the
     * scheduled job processor.</p>
     *
     * @param payloadJson the JSON payload to validate
     * @return the job ID for tracking
     */
    @Transactional
    public UUID submitValidationJob(String payloadJson) {
        TrustContext ctx = TrustContextHolder.require();

        ValidationJobEntity job = new ValidationJobEntity();
        job.setId(UUID.randomUUID());
        job.setTenantId(ctx.tenantId());
        job.setPayloadJson(payloadJson);
        job.setStatus(JobStatus.PENDING);
        job.setSubmittedBy(ctx.actorId());
        job.setCreatedAt(OffsetDateTime.now());

        validationJobRepository.save(job);

        log.info("Validation job submitted: id={}, tenant={}", job.getId(), ctx.tenantId());

        return job.getId();
    }

    /**
     * Process a single validation job.
     *
     * <p>Transitions the job from PENDING to PROCESSING, runs the validation,
     * and stores the results. On success the job transitions to COMPLETED;
     * on failure it transitions to FAILED with an error message.</p>
     *
     * @param jobId the job to process
     */
    @Transactional
    public void processJob(UUID jobId) {
        TrustContext ctx = TrustContextHolder.require();

        ValidationJobEntity job = validationJobRepository.findByTenantIdAndJobId(ctx.tenantId(), jobId)
                .orElseThrow(() -> new IllegalArgumentException("Validation job not found: " + jobId));

        if (job.getStatus() != JobStatus.PENDING) {
            log.warn("Job {} is not in PENDING status (current: {}), skipping", jobId, job.getStatus());
            return;
        }

        job.setStatus(JobStatus.PROCESSING);
        validationJobRepository.save(job);

        try {
            List<ValidationIssue> issues = validateResource(job.getPayloadJson(), null, null);

            String resultJson = objectMapper.writeValueAsString(issues);
            job.setResultJson(resultJson);
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(OffsetDateTime.now());

            validationJobRepository.save(job);

            log.info("Validation job completed: id={}, issueCount={}", jobId, issues.size());

            // If there are errors, write a VALIDATION_FAILED outbox event
            boolean hasErrors = issues.stream()
                    .anyMatch(i -> i.severity() == ValidationSeverity.ERROR);
            if (hasErrors) {
                writeOutbox("VALIDATION_JOB", jobId.toString(), "VALIDATION_FAILED", resultJson);
            }

        } catch (Exception e) {
            log.error("Validation job failed: id={}, error={}", jobId, e.getMessage(), e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(truncate(e.getMessage(), 2000));
            job.setCompletedAt(OffsetDateTime.now());
            validationJobRepository.save(job);
        }
    }

    /**
     * Scheduled task that polls for PENDING validation jobs and processes them.
     *
     * <p>Runs on a fixed 5-second delay. Each pending job is processed
     * individually within its own transaction boundary.</p>
     */
    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void processPendingJobs() {
        List<ValidationJobEntity> pendingJobs = validationJobRepository.findByStatus(JobStatus.PENDING);

        if (pendingJobs.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending validation jobs", pendingJobs.size());

        for (ValidationJobEntity job : pendingJobs) {
            // Every path below reaches TrustContextHolder.require(), which throws when unset. On a
            // scheduler thread there is no inbound request and therefore no context, so before this
            // block EVERY queued job threw, was caught, and landed in FAILED — the async validation
            // path could not have worked, and zibo_validation_jobs sitting at zero rows is why
            // nobody noticed.
            //
            // The context is built from the JOB's own tenant, not a single service-wide one.
            // findByStatus(PENDING) is deliberately not tenant-scoped, so a shared context would
            // process one tenant's payload under another's authority and write the result — and the
            // artifacts it resolved against — into the wrong plane.
            TrustContextHolder.set(systemContextFor(job));
            try {
                processJobInternal(job);
            } catch (Exception e) {
                log.error("Failed to process validation job {}: {}", job.getId(), e.getMessage(), e);
            } finally {
                TrustContextHolder.clear();
            }
        }
    }

    /**
     * The authority a scheduled validation job runs under.
     *
     * <p>Mirrors {@code ObservationDefinitionSeeder.systemContext()} — the estate's existing idiom
     * for background work that must still be tenant-scoped. Purpose is GOVERNANCE, not TREATMENT:
     * this is conformance checking of an already-recorded payload, not care delivery, and the audit
     * trail should say so.</p>
     */
    private static TrustContext systemContextFor(ValidationJobEntity job) {
        return new TrustContext(
                job.getTenantId(),
                "zibo-validation-scheduler",
                "SERVICE",
                "GOVERNANCE",
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                zw.gov.mohcc.impilo.shared.auth.AccessMode.INTERNAL);
    }

    /**
     * Retrieve the result of a validation job.
     *
     * @param jobId the job identifier
     * @return the validation job entity with its result
     * @throws IllegalArgumentException if the job is not found for this tenant
     */
    @Transactional(readOnly = true)
    public ValidationJobEntity getJobResult(UUID jobId) {
        TrustContext ctx = TrustContextHolder.require();
        return validationJobRepository.findByTenantIdAndJobId(ctx.tenantId(), jobId)
                .orElseThrow(() -> new IllegalArgumentException("Validation job not found: " + jobId));
    }

    /**
     * Resolve the effective policy mode for a given context.
     *
     * <p>Precedence: explicit override > workspace assignment > facility
     * assignment > tenant assignment > global default from configuration.</p>
     *
     * @param tenantId   the tenant identifier
     * @param facilityId the facility identifier; may be {@code null}
     * @param override   explicit override; may be {@code null}
     * @return the effective policy mode
     */
    public PolicyMode resolveEffectiveMode(UUID tenantId, UUID facilityId, PolicyMode override) {
        // Highest precedence: explicit override
        if (override != null) {
            return override;
        }

        // Check workspace-level assignment if workspace context is available
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null && ctx.workspaceId() != null) {
            Optional<PolicyMode> workspaceMode = findActivePolicyMode(
                    tenantId, ScopeType.WORKSPACE, ctx.workspaceId());
            if (workspaceMode.isPresent()) {
                return workspaceMode.get();
            }
        }

        // Check facility-level assignment
        if (facilityId != null) {
            Optional<PolicyMode> facilityMode = findActivePolicyMode(
                    tenantId, ScopeType.FACILITY, facilityId);
            if (facilityMode.isPresent()) {
                return facilityMode.get();
            }
        }

        // Check tenant-level assignment
        Optional<PolicyMode> tenantMode = findActivePolicyMode(
                tenantId, ScopeType.TENANT, tenantId);
        if (tenantMode.isPresent()) {
            return tenantMode.get();
        }

        // Fall back to global default
        String defaultModeStr = ziboProperties.getValidation().getDefaultMode();
        try {
            return PolicyMode.valueOf(defaultModeStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid default policy mode in configuration: '{}', falling back to LENIENT", defaultModeStr);
            return PolicyMode.LENIENT;
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void processJobInternal(ValidationJobEntity job) {
        if (job.getStatus() != JobStatus.PENDING) {
            return;
        }

        job.setStatus(JobStatus.PROCESSING);
        validationJobRepository.save(job);

        try {
            List<ValidationIssue> issues = validateResource(job.getPayloadJson(), null, null);

            String resultJson = objectMapper.writeValueAsString(issues);
            job.setResultJson(resultJson);
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(OffsetDateTime.now());
            validationJobRepository.save(job);

            log.info("Validation job completed: id={}, issueCount={}", job.getId(), issues.size());

            boolean hasErrors = issues.stream()
                    .anyMatch(i -> i.severity() == ValidationSeverity.ERROR);
            if (hasErrors) {
                writeOutbox("VALIDATION_JOB", job.getId().toString(), "VALIDATION_FAILED", resultJson);
            }
        } catch (Exception e) {
            log.error("Validation job failed: id={}, error={}", job.getId(), e.getMessage(), e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(truncate(e.getMessage(), 2000));
            job.setCompletedAt(OffsetDateTime.now());
            validationJobRepository.save(job);
        }
    }

    /**
     * Search a CodeSystem content JSON for a specific code.
     *
     * <p>Supports the standard FHIR CodeSystem structure where concepts
     * are in a {@code concept} array with {@code code} fields. Also supports
     * nested (hierarchical) concept structures.</p>
     */
    private boolean lookupCodeInContent(String contentJson, String code) {
        if (contentJson == null || contentJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            JsonNode concepts = root.path("concept");
            if (concepts.isArray()) {
                return searchConceptArray(concepts, code);
            }
            return false;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse CodeSystem content: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Recursively search a concept array for a matching code.
     * Supports hierarchical CodeSystem structures with nested concept arrays.
     */
    private boolean searchConceptArray(JsonNode concepts, String code) {
        for (JsonNode concept : concepts) {
            String conceptCode = concept.path("code").asText(null);
            if (code.equals(conceptCode)) {
                return true;
            }
            // Recurse into nested concepts
            JsonNode children = concept.path("concept");
            if (children.isArray() && searchConceptArray(children, code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract all Coding entries from a FHIR resource JSON tree.
     * Walks the entire JSON tree looking for objects that have both
     * "system" and "code" fields, typically found inside "coding" arrays.
     */
    private List<CodingEntry> extractCodings(JsonNode node) {
        List<CodingEntry> codings = new ArrayList<>();
        extractCodingsRecursive(node, codings);
        return codings;
    }

    private void extractCodingsRecursive(JsonNode node, List<CodingEntry> codings) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        if (node.isObject()) {
            // Check if this node looks like a Coding (has system + code)
            if (node.has("system") && node.has("code")) {
                String system = node.get("system").asText(null);
                String code = node.get("code").asText(null);
                String display = node.has("display") ? node.get("display").asText(null) : null;
                if (system != null && code != null) {
                    codings.add(new CodingEntry(system, code, display));
                }
            }
            // Recurse into all fields
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                extractCodingsRecursive(fields.next().getValue(), codings);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                extractCodingsRecursive(child, codings);
            }
        }
    }

    private Optional<PolicyMode> findActivePolicyMode(UUID tenantId, ScopeType scopeType, UUID scopeId) {
        List<AssignmentEntity> assignments =
                assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(
                        tenantId, scopeType, scopeId);

        return assignments.stream()
                .filter(a -> a.getPolicyMode() != null)
                .map(AssignmentEntity::getPolicyMode)
                .findFirst();
    }

    /**
     * Records what happened to one coding — <b>including when it resolved</b>.
     *
     * <p>This table held zero rows and, before {@code V400}, could not have answered the question
     * anyway: no {@code code} column, and only failures written. Failures counted against no
     * denominator, so a hundred of them might be a catastrophe or a rounding error and nothing on
     * record could tell you which. A {@code RESOLVED} row is not noise; it is the denominator, and
     * writing it is the entire point of Z1.</p>
     *
     * <p>Never throws. Telemetry that can fail a clinical validation is worse than no telemetry —
     * this is a measurement, and a measurement must not become an outage.</p>
     *
     * <p><b>No PHI.</b> The coding, the FHIR element path and the calling service go in; the
     * resource body does not, and {@code elementPath} is a path, never a value.</p>
     */
    private void logOutcome(UUID tenantId, UUID facilityId, ValidationSeverity severity,
                            String issueCode, String canonicalUrl, String version, String details,
                            String system, String code, ValidationOutcome outcome,
                            PolicyMode mode) {
        try {
            ValidationLogEntity logEntry = new ValidationLogEntity();
            logEntry.setId(UUID.randomUUID());
            logEntry.setTenantId(tenantId);
            logEntry.setFacilityId(facilityId);
            logEntry.setServiceName("zibo-service");
            logEntry.setSeverity(severity);
            logEntry.setIssueCode(issueCode);
            logEntry.setCanonicalUrl(canonicalUrl);
            logEntry.setVersion(version);
            logEntry.setDetails(details);
            logEntry.setSystem(system);
            logEntry.setCode(code);
            logEntry.setResult(outcome);
            logEntry.setPolicyMode(mode);
            logEntry.setCreatedAt(OffsetDateTime.now());

            TrustContext ctx = TrustContextHolder.get();
            if (ctx != null) {
                // service_name has always been the hardcoded literal "zibo-service", so it cannot
                // distinguish oros from butano from the BFF. The actor carries who actually asked.
                logEntry.setCallingService(ctx.actorId());
                if (ctx.correlationId() != null) {
                    logEntry.setCorrelationId(ctx.correlationId().toString());
                }
            }
            validationLogRepository.save(logEntry);

            Counter.builder("zibo_terminology_validation_total")
                    .description("Terminology validations by outcome. RESOLVED is the denominator "
                            + "for the estate's coding-coverage measurement.")
                    .tag("outcome", outcome.name())
                    .tag("system", system == null ? "unknown" : system)
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException e) {
            log.warn("ZIBO: could not record validation telemetry ({}); the validation itself is "
                    + "unaffected", e.getMessage());
        }
    }

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        TrustContext ctx = TrustContextHolder.get();
        if (ctx == null || ctx.tenantId() == null) {
            // zibo_event_outbox.tenant_id is NOT NULL, so saving here would fail the insert and
            // take the surrounding transaction with it — losing the job result to protect an event
            // nobody can route anyway. Refusing the event and keeping the result is the better
            // trade, and it is logged rather than swallowed.
            log.warn("ZIBO: no tenant in context — dropping {} outbox event for {} rather than "
                    + "failing the transaction that produced it", eventType, aggregateId);
            return;
        }
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // ------------------------------------------------------------------
    // Inner data classes
    // ------------------------------------------------------------------

    /**
     * Internal representation of a coding extracted from a FHIR resource.
     */
    private record CodingEntry(String system, String code, String display) {}

    /**
     * Result of a single coding validation.
     *
     * @param valid  {@code true} if the coding is valid (no ERROR-level issues)
     * @param issues the list of validation issues (may be empty if valid)
     */
    public record ValidationResult(boolean valid, List<ValidationIssue> issues) {}

    /**
     * A single validation issue describing a problem found during coding validation.
     *
     * @param severity     the severity of the issue (ERROR, WARNING, INFORMATION)
     * @param code         a machine-readable issue code (e.g. "code-invalid", "not-found")
     * @param message      a human-readable description of the issue
     * @param canonicalUrl the canonical URL of the relevant CodeSystem; may be {@code null}
     */
    public record ValidationIssue(ValidationSeverity severity, String code,
                                  String message, String canonicalUrl) {}
}
