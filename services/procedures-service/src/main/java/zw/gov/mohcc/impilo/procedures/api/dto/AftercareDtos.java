package zw.gov.mohcc.impilo.procedures.api.dto;

import java.util.List;

/** Shapes for recovery settings (pipeline §15) and aftercare templates (§17). */
public final class AftercareDtos {

    private AftercareDtos() {
    }

    public record RecoverySettingView(
            String settingCode,
            String settingLabel,
            Integer minimumObservationMinutes,
            String dischargeReadinessCriteria,
            String monitoringRequired) {
    }

    public record InstructionView(String instructionKind, String instructionText) {
    }

    public record AftercareTemplateView(
            String templateCode,
            String templateName,
            String applicableSetting,
            String description,
            String status,
            String approvingAuthority,
            String contentMaturity,
            List<InstructionView> instructions,
            List<String> deliveryChannels) {
    }
}
