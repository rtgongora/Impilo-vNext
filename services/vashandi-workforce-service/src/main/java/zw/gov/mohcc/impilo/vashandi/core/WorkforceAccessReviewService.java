package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AccessRiskEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AccessRiskRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkforceAccessReviewService {

    private final AccessRiskRepository accessRiskRepository;
    private final WorkforceProfileRepository profileRepository;
    private final WorkforceAssignmentRepository assignmentRepository;
    private final VashandiOutboxWriter outboxWriter;

    public WorkforceAccessReviewService(AccessRiskRepository accessRiskRepository,
                                        WorkforceProfileRepository profileRepository,
                                        WorkforceAssignmentRepository assignmentRepository,
                                        VashandiOutboxWriter outboxWriter) {
        this.accessRiskRepository = accessRiskRepository;
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxWriter = outboxWriter;
    }

    public List<AccessRiskEntity> list(UUID tenantId, String status) {
        if (status != null && !status.isBlank()) {
            return accessRiskRepository.findByTenantIdAndStatusOrderBySeverityDescDetectedAtDesc(tenantId, status);
        }
        return accessRiskRepository.findByTenantIdOrderByDetectedAtDesc(tenantId);
    }

    @Transactional
    public VashandiDtos.AccessRiskScanResponse scan(UUID tenantId) throws Exception {
        List<AccessRiskEntity> detected = new ArrayList<>();
        List<WorkforceProfileEntity> profiles = profileRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        for (WorkforceProfileEntity profile : profiles) {
            List<WorkforceAssignmentEntity> activeAssignments = assignmentRepository
                    .findByTenantIdAndWorkforceProfileIdAndStatusIn(
                            tenantId, profile.getId(), List.of("active", "approved"));
            if (activeAssignments.isEmpty() && "active".equals(profile.getCurrentStatus())) {
                detected.add(saveRisk(tenantId, profile.getId(),
                        "active_access_without_assignment", "high",
                        "Revoke or create assignment before Work access"));
            }
            if ("suspended".equals(profile.getCurrentStatus()) && !activeAssignments.isEmpty()) {
                detected.add(saveRisk(tenantId, profile.getId(),
                        "public_sector_access_with_hsc_suspension", "critical",
                        "Suspend assignments for suspended worker"));
            }
            if ("offboarded".equals(profile.getCurrentStatus()) && profile.getKeycloakUserId() != null) {
                detected.add(saveRisk(tenantId, profile.getId(),
                        "dormant_account_with_active_roles", "high",
                        "Revoke Keycloak-linked access"));
            }
        }
        for (AccessRiskEntity risk : detected) {
            emitRisk(tenantId, risk, "detected");
        }
        return new VashandiDtos.AccessRiskScanResponse(detected.size(), detected, "completed");
    }

    @Transactional
    public AccessRiskEntity resolve(UUID tenantId, UUID riskId) throws Exception {
        AccessRiskEntity risk = accessRiskRepository.findByTenantIdAndId(tenantId, riskId)
                .orElseThrow(() -> new IllegalArgumentException("access risk not found"));
        risk.setStatus("resolved");
        risk.setResolvedBy(actorId());
        risk.setResolvedAt(OffsetDateTime.now());
        AccessRiskEntity saved = accessRiskRepository.save(risk);
        emitRisk(tenantId, saved, "resolved");
        return saved;
    }

    @Transactional
    public VashandiDtos.AccessRiskActionResponse revokeAccess(UUID tenantId, UUID riskId) throws Exception {
        AccessRiskEntity risk = accessRiskRepository.findByTenantIdAndId(tenantId, riskId)
                .orElseThrow(() -> new IllegalArgumentException("access risk not found"));
        risk.setStatus("revoked");
        risk.setResolvedBy(actorId());
        risk.setResolvedAt(OffsetDateTime.now());
        accessRiskRepository.save(risk);

        profileRepository.findByTenantIdAndId(tenantId, risk.getWorkforceProfileId()).ifPresent(profile -> {
            profile.setAccessRiskStatus("revoked");
            profile.setCurrentStatus("offboarded");
            profileRepository.save(profile);
        });

        Map<String, Object> payload = new HashMap<>();
        payload.put("riskId", risk.getId().toString());
        outboxWriter.publish(tenantId, "ACCESS_RISK", risk.getId().toString(), "access_risk", "revoked",
                "vashandi:access:revoked:" + risk.getId(), payload);

        return new VashandiDtos.AccessRiskActionResponse(risk.getId(), "completed", "access revoked");
    }

    private AccessRiskEntity saveRisk(UUID tenantId, UUID profileId, String riskType, String severity, String action) {
        AccessRiskEntity risk = new AccessRiskEntity();
        risk.setTenantId(tenantId);
        risk.setWorkforceProfileId(profileId);
        risk.setRiskType(riskType);
        risk.setSeverity(severity);
        risk.setRecommendedAction(action);
        return accessRiskRepository.save(risk);
    }

    private void emitRisk(UUID tenantId, AccessRiskEntity risk, String action) throws Exception {
        Map<String, Object> payload = Map.of(
                "riskId", risk.getId().toString(),
                "riskType", risk.getRiskType(),
                "severity", risk.getSeverity());
        outboxWriter.publish(tenantId, "ACCESS_RISK", risk.getId().toString(), "access_risk", action,
                "vashandi:access_risk:" + action + ":" + risk.getId(), payload);
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }
}
