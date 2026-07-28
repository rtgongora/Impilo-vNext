package zw.gov.mohcc.impilo.procedures.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procedures.api.dto.SafetyPauseDtos.*;
import zw.gov.mohcc.impilo.procedures.persistence.entity.SafetyPauseTemplateEntity;
import zw.gov.mohcc.impilo.procedures.persistence.entity.SedationLevelEntity;
import zw.gov.mohcc.impilo.procedures.persistence.repository.SafetyPauseConfirmationItemRepository;
import zw.gov.mohcc.impilo.procedures.persistence.repository.SafetyPauseTemplateRepository;
import zw.gov.mohcc.impilo.procedures.persistence.repository.SedationLevelRepository;

import java.util.List;
import java.util.UUID;

/**
 * Read access to safety-pause templates (§9) and the sedation continuum (§10).
 *
 * <p>Both are governed content, read-only here — engine-not-store. A safety-pause
 * <em>completion</em> and a sedation <em>encounter</em> are execution records that belong to
 * whichever service performs the procedure; this service only tells a caller what is
 * required.</p>
 */
@Service
public class SafetyPauseAndSedationService {

    private static final String PUBLISHED = "PUBLISHED";

    private final SafetyPauseTemplateRepository templates;
    private final SafetyPauseConfirmationItemRepository confirmationItems;
    private final SedationLevelRepository sedationLevels;

    public SafetyPauseAndSedationService(SafetyPauseTemplateRepository templates,
                                         SafetyPauseConfirmationItemRepository confirmationItems,
                                         SedationLevelRepository sedationLevels) {
        this.templates = templates;
        this.confirmationItems = confirmationItems;
        this.sedationLevels = sedationLevels;
    }

    @Transactional(readOnly = true)
    public SafetyPauseTemplateView safetyPauseTemplate(UUID tenantId, String templateCode) {
        SafetyPauseTemplateEntity t = templates
                .findByTenantIdAndTemplateCodeAndStatus(tenantId, templateCode, PUBLISHED)
                .orElseThrow(() -> new ProceduresDomainException(
                        "SAFETY_PAUSE_TEMPLATE_NOT_FOUND", 404,
                        "No published safety-pause template '" + templateCode + "'"));
        List<ConfirmationItemView> items = confirmationItems
                .findByTemplateIdOrderByDisplayOrderAsc(t.getId()).stream()
                .map(i -> new ConfirmationItemView(i.getConfirmationItem(), i.getPromptText()))
                .toList();
        return new SafetyPauseTemplateView(t.getTemplateCode(), t.getTemplateName(),
                t.getApplicableSetting(), t.getDescription(), t.getStatus(),
                t.getApprovingAuthority(), items);
    }

    @Transactional(readOnly = true)
    public List<SedationLevelView> sedationLevels(UUID tenantId) {
        List<SedationLevelEntity> all = sedationLevels.findByTenantIdOrderByDepthRankAsc(tenantId);
        return all.stream().map(level -> toView(level, all)).toList();
    }

    @Transactional(readOnly = true)
    public SedationLevelView sedationLevel(UUID tenantId, String levelCode) {
        List<SedationLevelEntity> all = sedationLevels.findByTenantIdOrderByDepthRankAsc(tenantId);
        SedationLevelEntity level = all.stream()
                .filter(l -> l.getLevelCode().equals(levelCode))
                .findFirst()
                .orElseThrow(() -> new ProceduresDomainException(
                        "SEDATION_LEVEL_NOT_FOUND", 404, "No sedation level '" + levelCode + "'"));
        return toView(level, all);
    }

    private SedationLevelView toView(SedationLevelEntity level, List<SedationLevelEntity> all) {
        RescueCapability rescue = null;
        if (level.getRescueCapabilityLevelCode() != null) {
            rescue = all.stream()
                    .filter(l -> l.getLevelCode().equals(level.getRescueCapabilityLevelCode()))
                    .findFirst()
                    .map(l -> new RescueCapability(l.getLevelCode(), l.getLevelLabel(),
                            l.getMonitoringRequired(), l.getProviderCompetenceRequired()))
                    .orElse(null);
        }
        return new SedationLevelView(level.getLevelCode(), level.getLevelLabel(),
                level.getDepthRank(), level.getMonitoringRequired(),
                level.getProviderCompetenceRequired(), level.getTypicalRecoveryCriteria(), rescue);
    }
}
