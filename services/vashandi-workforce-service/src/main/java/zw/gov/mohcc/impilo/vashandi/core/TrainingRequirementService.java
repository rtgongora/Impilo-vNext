package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TrainingRequirementEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.TrainingRequirementRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Governance CRUD for the role-template → required-Fundo-course mapping
 * (PO-20260629-01). Every change is an audited outbox event; the default
 * enforcement level is ADVISORY (the PO's conservative-safe default — a new
 * requirement never silently blocks a licensed provider).
 */
@Service
public class TrainingRequirementService {

    private static final Set<String> LEVELS = Set.of("ADVISORY", "SOFT", "HARD");

    private final TrainingRequirementRepository repository;
    private final VashandiOutboxWriter outboxWriter;

    public TrainingRequirementService(TrainingRequirementRepository repository, VashandiOutboxWriter outboxWriter) {
        this.repository = repository;
        this.outboxWriter = outboxWriter;
    }

    public List<TrainingRequirementEntity> list(UUID tenantId) {
        return repository.findByTenantIdOrderByRoleTemplateIdAscCourseCodeAsc(tenantId);
    }

    public List<TrainingRequirementEntity> activeForRole(UUID tenantId, String roleTemplateId) {
        if (roleTemplateId == null || roleTemplateId.isBlank()) {
            return List.of();
        }
        return repository.findByTenantIdAndRoleTemplateIdAndActiveTrue(tenantId, roleTemplateId);
    }

    @Transactional
    public TrainingRequirementEntity create(
            UUID tenantId, String roleTemplateId, String courseCode, String enforcementLevel,
            String note, String createdBy) throws Exception {
        if (roleTemplateId == null || roleTemplateId.isBlank()) {
            throw new IllegalArgumentException("roleTemplateId is required");
        }
        if (courseCode == null || courseCode.isBlank()) {
            throw new IllegalArgumentException("courseCode is required");
        }
        String level = enforcementLevel == null || enforcementLevel.isBlank()
                ? "ADVISORY"
                : enforcementLevel.trim().toUpperCase();
        if (!LEVELS.contains(level)) {
            throw new IllegalArgumentException("enforcementLevel must be one of " + LEVELS);
        }
        TrainingRequirementEntity existing = repository
                .findByTenantIdAndRoleTemplateIdAndCourseCode(tenantId, roleTemplateId.trim(), courseCode.trim())
                .orElse(null);
        TrainingRequirementEntity row = existing != null ? existing : new TrainingRequirementEntity();
        row.setTenantId(tenantId);
        row.setRoleTemplateId(roleTemplateId.trim());
        row.setCourseCode(courseCode.trim());
        row.setEnforcementLevel(level);
        row.setActive(true);
        row.setNote(note);
        if (existing == null) {
            row.setCreatedBy(createdBy);
        }
        TrainingRequirementEntity saved = repository.save(row);
        outboxWriter.publish(tenantId, "TRAINING_REQUIREMENT", saved.getId().toString(),
                "training_requirement", existing == null ? "created" : "updated",
                "vashandi:training-requirement:upsert:" + saved.getId() + ":" + level,
                Map.of(
                        "requirementId", saved.getId().toString(),
                        "roleTemplateId", saved.getRoleTemplateId(),
                        "courseCode", saved.getCourseCode(),
                        "enforcementLevel", saved.getEnforcementLevel()));
        return saved;
    }

    @Transactional
    public TrainingRequirementEntity deactivate(UUID tenantId, UUID id) throws Exception {
        TrainingRequirementEntity row = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("training requirement not found"));
        row.setActive(false);
        TrainingRequirementEntity saved = repository.save(row);
        outboxWriter.publish(tenantId, "TRAINING_REQUIREMENT", saved.getId().toString(),
                "training_requirement", "deactivated",
                "vashandi:training-requirement:deactivate:" + saved.getId(),
                Map.of(
                        "requirementId", saved.getId().toString(),
                        "roleTemplateId", saved.getRoleTemplateId(),
                        "courseCode", saved.getCourseCode()));
        return saved;
    }
}
