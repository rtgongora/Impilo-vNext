package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps appointment provider/facility targets to concrete inbox recipients
 * ({@code actor:}, {@code staff:}, {@code facility-scheduling:}) for notification delivery.
 */
@Component
public class AppointmentProviderRecipientResolver {

    // Repointed from tuso: this resolver read the on-call rota from tuso /v1/staffing/on-call, a
    // path no service serves, so resolveFacilityStaff has always come back empty and appointment
    // notifications never reached facility staff. Rostering is vashandi's.
    private final VashandiServiceClient vashandiClient;

    public AppointmentProviderRecipientResolver(VashandiServiceClient vashandiClient) {
        this.vashandiClient = vashandiClient;
    }

    public List<String> resolve(String providerId, String facilityId) {
        Set<String> recipients = new LinkedHashSet<>();
        if (providerId != null && !providerId.isBlank()) {
            recipients.add("actor:" + providerId.trim());
            return List.copyOf(recipients);
        }
        if (facilityId != null && !facilityId.isBlank()) {
            recipients.addAll(resolveFacilityStaff(facilityId.trim()));
            if (recipients.isEmpty()) {
                recipients.add("facility-scheduling:" + facilityId.trim());
            }
        }
        return List.copyOf(recipients);
    }

    private List<String> resolveFacilityStaff(String facilityId) {
        Set<String> recipients = new LinkedHashSet<>();
        try {
            String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).toString();
            JsonNode onCall = vashandiClient.listOnCall(facilityId, weekStart);
            if (onCall != null && onCall.isArray()) {
                for (JsonNode row : onCall) {
                    // The rota is a resource envelope: identifiers live under "attributes", and the
                    // person reference vashandi holds is the workforce profile id. Reading the old
                    // flat provider_id/staff_id keys would compile, run, and quietly resolve nobody
                    // — which is how this path came to be silently empty in the first place.
                    JsonNode attributes = row.has("attributes") ? row.get("attributes") : row;
                    addIfPresent(recipients, attributes,
                            "primary_workforce_profile_id", "primaryWorkforceProfileId", "staff:");
                    addIfPresent(recipients, attributes,
                            "backup_workforce_profile_id", "backupWorkforceProfileId", "staff:");
                }
            }
        } catch (Exception ignored) {
            // upstream unavailable — fall back to facility-scheduling group
        }
        return new ArrayList<>(recipients);
    }

    private static void addIfPresent(Set<String> recipients, JsonNode row, String snake, String camel, String prefix) {
        String value = text(row, snake, camel);
        if (value != null && !value.isBlank()) {
            recipients.add(prefix + value.trim());
        }
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                String value = node.get(field).asText("").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
