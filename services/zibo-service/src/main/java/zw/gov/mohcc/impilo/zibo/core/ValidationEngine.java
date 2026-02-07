package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public ValidationEngine(ArtifactRepository artifactRepository,
                            AssignmentRepository assignmentRepository,
                            ValidationJobRepository validationJobRepository,
                            ValidationLogRepository validationLogRepository,
                            EventOutboxRepository outboxRepository,
                            ZiboProperties ziboProperties,
                            ObjectMapper objectMapper) {
        this.artifactRepository = artifactRepository;
        this.assignmentRepository = assignmentRepository;
        this.validationJobRepository = validationJobRepository;
        this.validationLogRepository = validationLogRepository;
        this.outboxRepository = outboxRepository;
        this.ziboProperties = ziboProperties;
        this.objectMapper = objectMapper;
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

        // Look up the CodeSystem artifact by canonical URL
        List<ArtifactEntity> artifacts = artifactRepository.findByTenantIdAndCanonicalUrl(tenantId, system);

        // Filter to published CodeSystem artifacts only, pick the latest
        Optional<ArtifactEntity> codeSystemOpt = artifacts.stream()
                .filter(a -> a.getFhirType() == ArtifactType.CODE_SYSTEM)
                .filter(a -> a.getStatus() == ArtifactStatus.PUBLISHED)
                .max(Comparator.comparing(ArtifactEntity::getPublishedAt));

        if (codeSystemOpt.isEmpty()) {
            // No CodeSystem found for this system
            ValidationSeverity severity = effectiveMode == PolicyMode.STRICT
                    ? ValidationSeverity.ERROR : ValidationSeverity.WARNING;

            ValidationIssue issue = new ValidationIssue(
                    severity,
                    "not-found",
                    "CodeSystem not found for system: " + system,
                    system);

            logValidation(tenantId, facilityId, severity, "not-found", system, null,
                    "CodeSystem not found for system: " + system);

            boolean valid = severity != ValidationSeverity.ERROR;
            return new ValidationResult(valid, List.of(issue));
        }

        ArtifactEntity codeSystem = codeSystemOpt.get();
        boolean codeFound = lookupCodeInContent(codeSystem.getContentJson(), code);

        if (codeFound) {
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

        logValidation(tenantId, facilityId, severity, "code-invalid",
                system, codeSystem.getVersion(),
                "Code '" + code + "' not found in CodeSystem");

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
            try {
                processJobInternal(job);
            } catch (Exception e) {
                log.error("Failed to process validation job {}: {}", job.getId(), e.getMessage(), e);
            }
        }
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

    private void logValidation(UUID tenantId, UUID facilityId, ValidationSeverity severity,
                               String issueCode, String canonicalUrl, String version, String details) {
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
        logEntry.setCreatedAt(OffsetDateTime.now());
        validationLogRepository.save(logEntry);
    }

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null) {
            outbox.setTenantId(ctx.tenantId());
        }
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
