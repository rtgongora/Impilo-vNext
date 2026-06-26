package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditFindingEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditSectionEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditToolEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.AuditFindingRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.AuditRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.AuditSectionRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.AuditToolRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Owning service for the Rito audit aggregates: audit tools and their sections
 * ({@code rit_audit_tool}, {@code rit_audit_section}), audits ({@code rit_audit}) and their
 * findings ({@code rit_audit_finding}).
 *
 * <p>An audit is scheduled against a reusable tool, started, populated with per-item findings,
 * then submitted — at which point a compliance score is computed from the findings (compliant
 * items count fully, partial items count half; not-applicable and observation items are
 * excluded from the denominator).
 */
@Service
public class AuditService {

    private static final Set<String> FINDING_TYPES =
            Set.of("COMPLIANT", "NON_COMPLIANT", "PARTIAL", "NOT_APPLICABLE", "OBSERVATION");

    private final AuditRepository auditRepository;
    private final AuditToolRepository auditToolRepository;
    private final AuditSectionRepository auditSectionRepository;
    private final AuditFindingRepository auditFindingRepository;
    private final ReferenceGenerator referenceGenerator;
    private final RitoEventEmitter emitter;

    public AuditService(AuditRepository auditRepository,
                        AuditToolRepository auditToolRepository,
                        AuditSectionRepository auditSectionRepository,
                        AuditFindingRepository auditFindingRepository,
                        ReferenceGenerator referenceGenerator,
                        RitoEventEmitter emitter) {
        this.auditRepository = auditRepository;
        this.auditToolRepository = auditToolRepository;
        this.auditSectionRepository = auditSectionRepository;
        this.auditFindingRepository = auditFindingRepository;
        this.referenceGenerator = referenceGenerator;
        this.emitter = emitter;
    }

    // ---- audit tools (rit_audit_tool, rit_audit_section) ----

    @Transactional
    public AuditToolEntity createTool(UUID tenantId, String toolKey, String name, String description,
                                      String version, String auditType, String formSchemaRef, BigDecimal maxScore) {
        require(toolKey, "toolKey");
        if (auditToolRepository.existsByTenantIdAndToolKey(tenantId, toolKey)) {
            throw new IllegalArgumentException("tool key exists");
        }
        AuditToolEntity tool = new AuditToolEntity();
        tool.setTenantId(tenantId);
        tool.setToolKey(toolKey);
        tool.setName(require(name, "name"));
        tool.setDescription(description);
        tool.setVersion(version != null ? version : "1");
        tool.setAuditType(auditType);
        tool.setFormSchemaRef(formSchemaRef);
        tool.setMaxScore(maxScore);
        tool.setActive(true);
        return auditToolRepository.save(tool);
    }

    @Transactional
    public AuditSectionEntity addSection(UUID tenantId, UUID toolId, String sectionKey, String title,
                                         Integer ordinal, BigDecimal weight, BigDecimal maxScore) {
        loadTool(tenantId, toolId);
        AuditSectionEntity section = new AuditSectionEntity();
        section.setAuditToolId(toolId);
        section.setTenantId(tenantId);
        section.setSectionKey(require(sectionKey, "sectionKey"));
        section.setTitle(require(title, "title"));
        section.setOrdinal(ordinal != null ? ordinal : 0);
        section.setWeight(weight != null ? weight : BigDecimal.ONE);
        section.setMaxScore(maxScore);
        return auditSectionRepository.save(section);
    }

    @Transactional(readOnly = true)
    public List<AuditToolEntity> listTools(UUID tenantId) {
        return auditToolRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    @Transactional(readOnly = true)
    public AuditToolEntity getTool(UUID tenantId, UUID id) {
        return loadTool(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<AuditSectionEntity> listSections(UUID toolId) {
        return auditSectionRepository.findByAuditToolIdOrderByOrdinalAsc(toolId);
    }

    // ---- audits (rit_audit, rit_audit_finding) ----

    @Transactional
    public AuditEntity createAudit(UUID tenantId, UUID auditToolId, String auditType, UUID facilityId,
                                   UUID districtId, UUID provinceId, String leadAuditorActorId,
                                   OffsetDateTime scheduledAt, String createdBy) {
        if (auditType == null || auditType.isBlank()) {
            throw new IllegalArgumentException("auditType is required");
        }
        AuditEntity audit = new AuditEntity();
        audit.setTenantId(tenantId);
        audit.setAuditReference(referenceGenerator.generate("RITO-A",
                ref -> auditRepository.existsByTenantIdAndAuditReference(tenantId, ref)));
        audit.setAuditToolId(auditToolId);
        audit.setAuditType(auditType);
        audit.setFacilityId(facilityId);
        audit.setDistrictId(districtId);
        audit.setProvinceId(provinceId);
        audit.setLeadAuditorActorId(leadAuditorActorId);
        audit.setScheduledAt(scheduledAt);
        audit.setStatus("PLANNED");
        audit.setMetadataJson("{}");
        audit.setCreatedBy(createdBy);
        return auditRepository.save(audit);
    }

    @Transactional
    public AuditEntity startAudit(UUID tenantId, UUID id) {
        AuditEntity audit = loadAudit(tenantId, id);
        if (!"PLANNED".equals(audit.getStatus())) {
            throw new IllegalStateException("Audit can only be started from PLANNED, was " + audit.getStatus());
        }
        audit.setStatus("IN_PROGRESS");
        audit.setStartedAt(OffsetDateTime.now());
        auditRepository.save(audit);

        emitAuditEvent(audit, "rito.audit.started");
        return audit;
    }

    @Transactional
    public AuditFindingEntity addFinding(UUID tenantId, UUID auditId, String sectionKey, String itemRef,
                                         String findingType, String severity, String description, String evidence,
                                         String recommendedAction, BigDecimal score) {
        loadAudit(tenantId, auditId);
        if (findingType == null || !FINDING_TYPES.contains(findingType)) {
            throw new IllegalArgumentException(
                    "findingType must be one of COMPLIANT/NON_COMPLIANT/PARTIAL/NOT_APPLICABLE/OBSERVATION");
        }
        AuditFindingEntity finding = new AuditFindingEntity();
        finding.setAuditId(auditId);
        finding.setTenantId(tenantId);
        finding.setSectionKey(sectionKey);
        finding.setItemRef(itemRef);
        finding.setFindingType(findingType);
        finding.setSeverity(severity);
        finding.setDescription(description);
        finding.setEvidence(evidence);
        finding.setRecommendedAction(recommendedAction);
        finding.setScore(score);
        auditFindingRepository.save(finding);

        Map<String, Object> payload = new HashMap<>();
        payload.put("auditId", auditId.toString());
        payload.put("findingId", finding.getId().toString());
        payload.put("findingType", findingType);
        payload.put("severity", severity);
        payload.put("sectionKey", sectionKey);
        emitter.emit("AUDIT", auditId.toString(), "rito.audit.finding.created", "AUDIT",
                auditId.toString(), payload, tenantId);
        return finding;
    }

    @Transactional
    public AuditEntity submitAudit(UUID tenantId, UUID id) {
        AuditEntity audit = loadAudit(tenantId, id);
        List<AuditFindingEntity> findings = auditFindingRepository.findByAuditId(id);

        int denominator = 0;
        double numerator = 0d;
        for (AuditFindingEntity f : findings) {
            String type = f.getFindingType();
            if ("NOT_APPLICABLE".equals(type) || "OBSERVATION".equals(type)) {
                continue;
            }
            denominator++;
            if ("COMPLIANT".equals(type)) {
                numerator += 1d;
            } else if ("PARTIAL".equals(type)) {
                numerator += 0.5d;
            }
        }
        if (denominator > 0) {
            BigDecimal num = BigDecimal.valueOf(numerator);
            BigDecimal den = BigDecimal.valueOf(denominator);
            audit.setScoreNumerator(num);
            audit.setScoreDenominator(den);
            audit.setScorePercent(num.divide(den, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        audit.setStatus("SUBMITTED");
        audit.setSubmittedAt(OffsetDateTime.now());
        auditRepository.save(audit);

        emitAuditEvent(audit, "rito.audit.submitted");
        return audit;
    }

    @Transactional
    public AuditEntity closeAudit(UUID tenantId, UUID id) {
        AuditEntity audit = loadAudit(tenantId, id);
        audit.setStatus("CLOSED");
        audit.setClosedAt(OffsetDateTime.now());
        return auditRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<AuditEntity> listAudits(UUID tenantId, String status) {
        if (status == null) {
            return auditRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return auditRepository.findByTenantIdAndStatus(tenantId, status);
    }

    @Transactional(readOnly = true)
    public AuditEntity getAudit(UUID tenantId, UUID id) {
        return loadAudit(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<AuditFindingEntity> listFindings(UUID auditId) {
        return auditFindingRepository.findByAuditId(auditId);
    }

    // ---- internal helpers ----

    private AuditToolEntity loadTool(UUID tenantId, UUID id) {
        return auditToolRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Audit tool not found: " + id));
    }

    private AuditEntity loadAudit(UUID tenantId, UUID id) {
        return auditRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Audit not found: " + id));
    }

    private void emitAuditEvent(AuditEntity audit, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("auditId", audit.getId().toString());
        payload.put("auditReference", audit.getAuditReference());
        payload.put("auditType", audit.getAuditType());
        payload.put("status", audit.getStatus());
        payload.put("facilityId", audit.getFacilityId() != null ? audit.getFacilityId().toString() : null);
        payload.put("scorePercent", audit.getScorePercent());
        emitter.emit("AUDIT", audit.getId().toString(), eventType, "AUDIT",
                audit.getId().toString(), payload, audit.getTenantId());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }
}
