package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TusoFacilityClaimClient;

import java.util.ArrayList;
import java.util.List;

/** Facility-management contexts from TUSO facility_admin_appointment. */
@Component
public class FacilityAdminContextSource {

    private final TusoFacilityClaimClient tusoClient;
    private final WorkModeResolution modeResolution;

    public FacilityAdminContextSource(TusoFacilityClaimClient tusoClient, WorkModeResolution modeResolution) {
        this.tusoClient = tusoClient;
        this.modeResolution = modeResolution;
    }

    public String systemName() {
        return "TUSO";
    }

    public WorkContextAdapterResult resolve(String actorHealthId) {
        if (actorHealthId == null || actorHealthId.isBlank()) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty(systemName()));
        }
        JsonNode appointments;
        try {
            appointments = tusoClient.appointmentsByPerson(actorHealthId);
        } catch (Exception e) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.degraded(systemName(), e.getMessage()));
        }
        if (appointments == null || !appointments.isArray() || appointments.isEmpty()) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty(systemName()));
        }

        List<ResolvedWorkContext> out = new ArrayList<>();
        for (JsonNode a : appointments) {
            out.add(toContext(a));
        }
        return new WorkContextAdapterResult(out, WorkContextSourceStatus.live(systemName()));
    }

    private ResolvedWorkContext toContext(JsonNode a) {
        String id = text(a, "id");
        String facilityUuid = text(a, "facilityUuid");
        String role = text(a, "role");

        WorkModeResolution.Resolution modes = modeResolution.resolveForRoleTemplate(role);
        String label = "Facility " + facilityUuid + (role != null ? " (" + role + ")" : "");

        return new ResolvedWorkContext(
                VashandiContextSource.contextIdFor(systemName(), id != null ? id : facilityUuid + ":" + role),
                "facility", systemName(), id,
                facilityUuid, null, null, null,
                role, List.of(), modes.availableModes(), modes.defaultMode(), modes.modeSource(),
                null, null, null, null, null,
                null, new ArrayList<>(), label, "oversight", 0);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
