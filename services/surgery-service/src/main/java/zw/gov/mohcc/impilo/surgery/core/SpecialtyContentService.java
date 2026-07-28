package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalOperativeTemplateEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalSpecialtyIndicationEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalOperativeTemplateRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalSpecialtyIndicationRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SB-2/SB-4 — read access to specialty content (surgical domain pack §6): "content over shared
 * infrastructure", the shared spine (S1-S14) built once, each of the fifteen specialties
 * contributing indications and operative templates into it rather than forking their own model
 * (audit consequence #4).
 */
@Service
public class SpecialtyContentService {

    public static final Set<String> SPECIALTIES = Set.of(
            "GENERAL_SURGERY", "ORTHOPAEDICS", "UROLOGY", "ENT", "OPHTHALMOLOGY",
            "COLORECTAL", "UPPER_GI_HPB", "BREAST_ENDOCRINE", "VASCULAR", "NEUROSURGERY",
            "CARDIOTHORACIC", "MAXILLOFACIAL", "PLASTICS", "PAEDIATRIC_SURGERY", "SURGICAL_ONCOLOGY");

    private final SurgicalSpecialtyIndicationRepository indicationRepository;
    private final SurgicalOperativeTemplateRepository templateRepository;

    public SpecialtyContentService(SurgicalSpecialtyIndicationRepository indicationRepository,
                                   SurgicalOperativeTemplateRepository templateRepository) {
        this.indicationRepository = indicationRepository;
        this.templateRepository = templateRepository;
    }

    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    private void requireValidSpecialty(String specialty) {
        if (!SPECIALTIES.contains(specialty)) {
            throw new SurgeryDomainException("INVALID_SPECIALTY", 400, "specialty must be one of " + SPECIALTIES);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> indicationsFor(String specialty) {
        requireValidSpecialty(specialty);
        return indicationRepository.findByTenantIdAndSpecialtyOrderByIndicationCodeAsc(currentTenant(), specialty)
                .stream().map(this::indicationView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> templatesFor(String specialty) {
        requireValidSpecialty(specialty);
        return templateRepository.findByTenantIdAndSpecialtyOrderByTemplateCodeAsc(currentTenant(), specialty)
                .stream().map(this::templateView).toList();
    }

    private Map<String, Object> indicationView(SurgicalSpecialtyIndicationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("specialty", e.getSpecialty());
        m.put("indicationCode", e.getIndicationCode());
        m.put("conditionDisplay", e.getConditionDisplay());
        m.put("typicalProcedure", e.getTypicalProcedure());
        m.put("urgencyGuidance", e.getUrgencyGuidance());
        m.put("nonOperativeAlternatives", e.getNonOperativeAlternatives());
        m.put("notes", e.getNotes());
        m.put("contentMaturity", e.getContentMaturity());
        m.put("approvingAuthority", e.getApprovingAuthority());
        return m;
    }

    private Map<String, Object> templateView(SurgicalOperativeTemplateEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("specialty", e.getSpecialty());
        m.put("templateCode", e.getTemplateCode());
        m.put("templateName", e.getTemplateName());
        m.put("typicalProcedure", e.getTypicalProcedure());
        m.put("position", e.getPosition());
        m.put("preparation", e.getPreparation());
        m.put("incision", e.getIncision());
        m.put("keySteps", e.getKeySteps());
        m.put("techniqueOptions", e.getTechniqueOptions());
        m.put("closurePlan", e.getClosurePlan());
        m.put("woundClassification", e.getWoundClassification());
        m.put("postopInstructions", e.getPostopInstructions());
        m.put("contentMaturity", e.getContentMaturity());
        m.put("approvingAuthority", e.getApprovingAuthority());
        return m;
    }
}
