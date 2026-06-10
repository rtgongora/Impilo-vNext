package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentEntity;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HscEmploymentService {

    private final HscEmploymentRepository repository;
    private final GovernanceEventService governanceEventService;

    public HscEmploymentService(HscEmploymentRepository repository, GovernanceEventService governanceEventService) {
        this.repository = repository;
        this.governanceEventService = governanceEventService;
    }

    public List<HscEmploymentEntity> search(UUID tenantId,
                                            String providerWorkerId,
                                            String healthId,
                                            String facilityId,
                                            String province,
                                            String district,
                                            String status) {
        return repository.search(tenantId, blankToNull(providerWorkerId), blankToNull(healthId),
                blankToNull(facilityId), blankToNull(province), blankToNull(district), blankToNull(status));
    }

    public HscEmploymentEntity get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("HSC employment record not found"));
    }

    @Transactional
    public HscEmploymentEntity upsert(UUID tenantId, Map<String, Object> request) {
        HscEmploymentEntity entity;
        Object idValue = request.get("employmentRecordId");
        if (idValue != null && !idValue.toString().isBlank()) {
            entity = get(UUID.fromString(idValue.toString()));
        } else {
            entity = new HscEmploymentEntity();
            entity.init(tenantId);
        }
        if (request.containsKey("providerWorkerId")) entity.setProviderWorkerId(str(request.get("providerWorkerId")));
        if (request.containsKey("linkedHealthId")) entity.setLinkedHealthId(str(request.get("linkedHealthId")));
        if (request.containsKey("employerOrganisationId")) entity.setEmployerOrganisationId(parseUuid(request.get("employerOrganisationId")));
        if (request.containsKey("employmentStatus")) entity.setEmploymentStatus(str(request.get("employmentStatus")));
        if (request.containsKey("postId")) entity.setPostId(str(request.get("postId")));
        if (request.containsKey("postTitle")) entity.setPostTitle(str(request.get("postTitle")));
        if (request.containsKey("grade")) entity.setGrade(str(request.get("grade")));
        if (request.containsKey("cadre")) entity.setCadre(str(request.get("cadre")));
        if (request.containsKey("establishmentUnit")) entity.setEstablishmentUnit(str(request.get("establishmentUnit")));
        if (request.containsKey("currentPostingProvince")) entity.setCurrentPostingProvince(str(request.get("currentPostingProvince")));
        if (request.containsKey("currentPostingDistrict")) entity.setCurrentPostingDistrict(str(request.get("currentPostingDistrict")));
        if (request.containsKey("currentPostingFacility")) entity.setCurrentPostingFacility(str(request.get("currentPostingFacility")));
        if (request.containsKey("currentPostingDepartment")) entity.setCurrentPostingDepartment(str(request.get("currentPostingDepartment")));
        if (request.containsKey("transferStatus")) entity.setTransferStatus(str(request.get("transferStatus")));
        if (request.containsKey("promotionStatus")) entity.setPromotionStatus(str(request.get("promotionStatus")));
        if (request.containsKey("disciplinaryEmploymentStatus")) entity.setDisciplinaryEmploymentStatus(str(request.get("disciplinaryEmploymentStatus")));
        if (request.containsKey("verificationStatus")) entity.setVerificationStatus(str(request.get("verificationStatus")));
        entity = repository.save(entity);
        governanceEventService.enqueue("HSC_EMPLOYMENT", entity.getId().toString(), "impilo.governance.hsc_employment.updated",
                Map.of("employmentRecordId", entity.getId().toString(), "providerWorkerId", entity.getProviderWorkerId()),
                tenantId, null);
        return entity;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static UUID parseUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
