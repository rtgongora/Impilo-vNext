package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Regulator contexts from org-registry regulatory appointments. */
@Component
public class RegulatoryContextSource {

    private final OrganizationRegistryServiceClient orgRegistryClient;
    private final WorkModeResolution modeResolution;

    public RegulatoryContextSource(OrganizationRegistryServiceClient orgRegistryClient, WorkModeResolution modeResolution) {
        this.orgRegistryClient = orgRegistryClient;
        this.modeResolution = modeResolution;
    }

    public String systemName() {
        return "ORG_REGISTRY";
    }

    public WorkContextAdapterResult resolve(String actorHealthId) {
        if (actorHealthId == null || actorHealthId.isBlank()) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty(systemName()));
        }
        JsonNode appointments;
        try {
            appointments = orgRegistryClient.listAppointmentsByPerson(actorHealthId);
        } catch (Exception e) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.degraded(systemName(), e.getMessage()));
        }
        if (appointments == null || !appointments.isArray() || appointments.isEmpty()) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty(systemName()));
        }

        List<ResolvedWorkContext> out = new ArrayList<>();
        for (JsonNode a : appointments) {
            String status = text(a, "status");
            if (status != null && !"ACTIVE".equalsIgnoreCase(status)) {
                continue;
            }
            if (isExpired(a)) {
                continue;
            }
            out.add(toContext(a));
        }
        return new WorkContextAdapterResult(out, WorkContextSourceStatus.live(systemName()));
    }

    private ResolvedWorkContext toContext(JsonNode a) {
        String id = text(a, "id");
        String organisationId = text(a, "organizationId");
        String roleCode = text(a, "roleCode");
        String jurisdictionCode = text(a, "jurisdictionCode");

        WorkModeResolution.Resolution modes = modeResolution.resolveForRoleTemplate(roleCode);
        String label = (jurisdictionCode != null ? jurisdictionCode + " — " : "")
                + (roleCode != null ? roleCode : "Regulatory appointment");

        return new ResolvedWorkContext(
                VashandiContextSource.contextIdFor(systemName(), id != null ? id : organisationId + ":" + roleCode),
                "regulator", systemName(), id,
                null, organisationId, jurisdictionCode, null,
                roleCode, List.of(), modes.availableModes(), modes.defaultMode(), modes.modeSource(),
                null, null, null, null, null,
                null, new ArrayList<>(), label, "oversight", 0);
    }

    private static boolean isExpired(JsonNode a) {
        String validTo = text(a, "validTo");
        if (validTo == null || validTo.isBlank()) {
            return false;
        }
        try {
            return LocalDate.parse(validTo.length() > 10 ? validTo.substring(0, 10) : validTo)
                    .isBefore(Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate());
        } catch (Exception e) {
            return false;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
