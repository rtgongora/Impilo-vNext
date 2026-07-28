package zw.gov.mohcc.impilo.procedures.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procedures.api.dto.AftercareDtos.*;
import zw.gov.mohcc.impilo.procedures.persistence.entity.AftercareTemplateEntity;
import zw.gov.mohcc.impilo.procedures.persistence.entity.RecoverySettingEntity;
import zw.gov.mohcc.impilo.procedures.persistence.repository.AftercareInstructionRepository;
import zw.gov.mohcc.impilo.procedures.persistence.repository.AftercareTemplateChannelRepository;
import zw.gov.mohcc.impilo.procedures.persistence.repository.AftercareTemplateRepository;
import zw.gov.mohcc.impilo.procedures.persistence.repository.RecoverySettingRepository;

import java.util.List;
import java.util.UUID;

/**
 * Read access to recovery settings (§15) and aftercare templates (§17).
 *
 * <p>Both are governed content, read-only here — engine-not-store. Actually completing a
 * recovery period or actually delivering an aftercare instruction (to Khuluma, Nompilo, a
 * printed handout) is an execution/delivery concern that belongs to whichever service performs
 * or discharges the procedure; this service only tells a caller what is required or what to
 * hand over.</p>
 */
@Service
public class RecoveryAndAftercareService {

    private static final String PUBLISHED = "PUBLISHED";

    private final RecoverySettingRepository recoverySettings;
    private final AftercareTemplateRepository templates;
    private final AftercareInstructionRepository instructions;
    private final AftercareTemplateChannelRepository channels;

    public RecoveryAndAftercareService(RecoverySettingRepository recoverySettings,
                                       AftercareTemplateRepository templates,
                                       AftercareInstructionRepository instructions,
                                       AftercareTemplateChannelRepository channels) {
        this.recoverySettings = recoverySettings;
        this.templates = templates;
        this.instructions = instructions;
        this.channels = channels;
    }

    @Transactional(readOnly = true)
    public List<RecoverySettingView> recoverySettings(UUID tenantId) {
        return recoverySettings.findByTenantIdOrderBySettingCodeAsc(tenantId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecoverySettingView recoverySetting(UUID tenantId, String settingCode) {
        RecoverySettingEntity s = recoverySettings.findByTenantIdAndSettingCode(tenantId, settingCode)
                .orElseThrow(() -> new ProceduresDomainException(
                        "RECOVERY_SETTING_NOT_FOUND", 404, "No recovery setting '" + settingCode + "'"));
        return toView(s);
    }

    @Transactional(readOnly = true)
    public AftercareTemplateView aftercareTemplate(UUID tenantId, String templateCode) {
        AftercareTemplateEntity t = templates
                .findByTenantIdAndTemplateCodeAndStatus(tenantId, templateCode, PUBLISHED)
                .orElseThrow(() -> new ProceduresDomainException(
                        "AFTERCARE_TEMPLATE_NOT_FOUND", 404,
                        "No published aftercare template '" + templateCode + "'"));
        List<InstructionView> instructionViews = instructions
                .findByTemplateIdOrderByDisplayOrderAsc(t.getId()).stream()
                .map(i -> new InstructionView(i.getInstructionKind(), i.getInstructionText()))
                .toList();
        List<String> channelCodes = channels.findByTemplateId(t.getId()).stream()
                .map(c -> c.getDeliveryChannel())
                .sorted()
                .toList();
        return new AftercareTemplateView(t.getTemplateCode(), t.getTemplateName(),
                t.getApplicableSetting(), t.getDescription(), t.getStatus(),
                t.getApprovingAuthority(), t.getContentMaturity(), instructionViews, channelCodes);
    }

    private RecoverySettingView toView(RecoverySettingEntity s) {
        return new RecoverySettingView(s.getSettingCode(), s.getSettingLabel(),
                s.getMinimumObservationMinutes(), s.getDischargeReadinessCriteria(),
                s.getMonitoringRequired());
    }
}
