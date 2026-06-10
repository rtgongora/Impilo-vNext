package zw.gov.mohcc.impilo.experience.bootstrap;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BootstrapPolicyService {

    public BootstrapDtos.BootstrapPolicyDecision evaluateStatus(
            BootstrapDtos.BootstrapStateRecord state,
            boolean activeNationalAdminExists,
            BootstrapProperties properties) {
        List<String> warnings = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        String decisionId = UUID.randomUUID().toString();

        if (!properties.isEnabled()) {
            blocked.add("Bootstrap is disabled.");
            return denied(decisionId, warnings, blocked);
        }
        if (activeNationalAdminExists && !state.recoveryMode()) {
            blocked.add("A national platform administrator already exists.");
            return denied(decisionId, warnings, blocked);
        }
        if (state.bootstrapClosed() && !state.recoveryMode()) {
            blocked.add("Bootstrap Mode is closed.");
            return denied(decisionId, warnings, blocked);
        }
        if (properties.isRequireMfa() && !"production".equalsIgnoreCase(properties.getEnvironment())) {
            warnings.add("MFA is required before bootstrap admin activation.");
        }
        return allowed(decisionId, warnings, blocked);
    }

    public BootstrapDtos.BootstrapPolicyDecision evaluateFirstAdmin(
            BootstrapDtos.BootstrapStateRecord state,
            boolean activeNationalAdminExists,
            BootstrapProperties properties,
            BootstrapDtos.BootstrapFirstAdminRequest request) {
        BootstrapDtos.BootstrapPolicyDecision base = evaluateStatus(state, activeNationalAdminExists, properties);
        if (!base.allowed()) return base;

        List<String> warnings = new ArrayList<>(base.warnings());
        List<String> blocked = new ArrayList<>();
        if (request.nominatedPerson() == null || str(request.nominatedPerson().get("officialEmail")).isBlank()) {
            blocked.add("Nominated person official email is required.");
        }
        if (request.sovereignOrganisation() == null || str(request.sovereignOrganisation().get("legalName")).isBlank()) {
            blocked.add("Sovereign organisation must be confirmed.");
        }
        if (blocked.isEmpty()) {
            warnings.add("Bootstrap administrator role is time-limited and converts to governed roles after activation.");
            return allowed(base.decisionId(), warnings, blocked);
        }
        return denied(base.decisionId(), warnings, blocked);
    }

    public BootstrapDtos.BootstrapPolicyDecision evaluateRecoveryOpen(BootstrapDtos.BootstrapStateRecord state, String ceremonyReference) {
        if (ceremonyReference == null || ceremonyReference.isBlank()) {
            return denied(UUID.randomUUID().toString(), List.of(), List.of("Recovery ceremony reference is required."));
        }
        if (state.recoveryMode()) {
            return allowed(UUID.randomUUID().toString(), List.of("Recovery mode already open."), List.of());
        }
        return allowed(UUID.randomUUID().toString(), List.of("Recovery ceremony will be audited."), List.of());
    }

    private static BootstrapDtos.BootstrapPolicyDecision allowed(String decisionId, List<String> warnings, List<String> blocked) {
        return new BootstrapDtos.BootstrapPolicyDecision(true, decisionId, warnings, blocked);
    }

    private static BootstrapDtos.BootstrapPolicyDecision denied(String decisionId, List<String> warnings, List<String> blocked) {
        return new BootstrapDtos.BootstrapPolicyDecision(false, decisionId, warnings, blocked);
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
