package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p><b>Repointed with the rest of on-call.</b> This asked {@code tuso} who was on call — a path no
 * service serves — and read fields ({@code primary_staff_id}) that no on-call projection has ever
 * produced. It therefore resolved nobody and fell through to the facility group on every
 * appointment, silently. On-call is vashandi's projection over {@code vsh_shift}, and its rows carry
 * workforce profile ids under {@code attributes}, so that is what is read here.</p>
 */
@Component
public class AppointmentProviderRecipientResolver {

    private static final Logger log = LoggerFactory.getLogger(AppointmentProviderRecipientResolver.class);

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
                    JsonNode attributes = row.has("attributes") ? row.path("attributes") : row;
                    addIfPresent(recipients, attributes,
                            "primary_workforce_profile_id", "primaryWorkforceProfileId", "staff:");
                    addIfPresent(recipients, attributes,
                            "backup_workforce_profile_id", "backupWorkforceProfileId", "staff:");
                }
            }
        } catch (Exception e) {
            // The facility-scheduling group is a real broadcast target, not a stand-in for the
            // people on call — so a failure here changes who gets told, and says so in the log.
            log.warn("ON_CALL_RECIPIENTS_UNAVAILABLE facility={} falling back to the scheduling group: {}",
                    facilityId, e.getMessage());
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
