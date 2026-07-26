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

    // Rostering and on-call are vashandi's, not tuso's. TusoServiceClient's staffing
    // methods called tuso /v1/staffing/*, which no service serves, and were removed with a
    // docblock saying not to reintroduce them; this caller was left pointing at one of
    // them by a different lane, which is why the module stopped compiling.
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
                    addIfPresent(recipients, row, "provider_id", "providerId", "actor:");
                    addIfPresent(recipients, row, "staff_id", "staffId", "staff:");
                    addIfPresent(recipients, row, "primary_staff_id", "primaryStaffId", "staff:");
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
