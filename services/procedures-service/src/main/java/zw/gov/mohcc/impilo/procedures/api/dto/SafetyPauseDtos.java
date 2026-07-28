package zw.gov.mohcc.impilo.procedures.api.dto;

import java.util.List;

/** Shapes for safety-pause templates (pipeline §9) and the sedation continuum (§10). */
public final class SafetyPauseDtos {

    private SafetyPauseDtos() {
    }

    public record ConfirmationItemView(String confirmationItem, String promptText) {
    }

    public record SafetyPauseTemplateView(
            String templateCode,
            String templateName,
            String applicableSetting,
            String description,
            String status,
            String approvingAuthority,
            List<ConfirmationItemView> confirmationItems) {
    }

    /**
     * A sedation level with its rescue-capability requirement RESOLVED, not just referenced —
     * the caller gets the next level's actual monitoring and provider requirements inline,
     * because that is the standard's own point: requirements follow the depth that may be
     * reached, and a caller should not have to make a second call to find out what that means.
     */
    public record SedationLevelView(
            String levelCode,
            String levelLabel,
            int depthRank,
            String monitoringRequired,
            String providerCompetenceRequired,
            String typicalRecoveryCriteria,
            RescueCapability rescueCapability) {
    }

    public record RescueCapability(
            String levelCode,
            String levelLabel,
            String monitoringRequired,
            String providerCompetenceRequired) {
    }
}
