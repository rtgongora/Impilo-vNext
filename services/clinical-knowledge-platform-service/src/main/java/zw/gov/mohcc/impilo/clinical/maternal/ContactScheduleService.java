package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Loads the antenatal contact schedule and runs {@link ContactScheduleEngine} against it.
 *
 * <p>The engine holds no content; this is the only class that knows where the schedule lives, so a
 * ministry can change the timing without a code change.
 */
@Service
public class ContactScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ContactScheduleService.class);

    static final String CONTENT_PATH = "clinical/rmnp-anc-schedule.json";

    private final ObjectMapper objectMapper;
    private volatile ContactScheduleEngine.Schedule cached;

    public ContactScheduleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ContactScheduleEngine.ScheduleForecast forecast(Integer gestationalAgeDays,
                                                           Set<Integer> completedContacts) {
        return ContactScheduleEngine.forecast(schedule(), gestationalAgeDays, completedContacts);
    }

    public ContactScheduleEngine.Schedule schedule() {
        ContactScheduleEngine.Schedule local = cached;
        if (local == null) {
            local = read(CONTENT_PATH);
            cached = local;
        }
        return local;
    }

    ContactScheduleEngine.Schedule read(String resourcePath) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.error("Antenatal contact schedule not found on the classpath: {}", resourcePath);
                return null;
            }
            JsonNode root = objectMapper.readTree(stream);
            List<ContactScheduleEngine.ContactDefinition> contacts = new ArrayList<>();
            for (JsonNode node : root.path("contacts")) {
                contacts.add(new ContactScheduleEngine.ContactDefinition(
                        node.path("contactNumber").asInt(),
                        node.path("name").asText(),
                        node.path("targetWeek").asInt(),
                        node.path("opensWeek").asInt(),
                        node.path("overdueAfterWeek").asInt(),
                        strings(node.path("keyActions")),
                        node.path("note").asText(null)));
            }
            ContactScheduleEngine.Schedule schedule = new ContactScheduleEngine.Schedule(
                    root.path("totalContacts").asInt(contacts.size()),
                    List.copyOf(contacts),
                    root.path("postTerm").path("fromWeek").asInt(41),
                    root.path("postTerm").path("message").asText(
                            "Post-term — the routine schedule no longer applies."));
            log.info("Loaded antenatal contact schedule {} version={} contacts={} postTermFrom={}",
                    root.path("scheduleId").asText(resourcePath),
                    root.path("version").asText("unknown"), contacts.size(),
                    schedule.postTermFromWeek());
            return schedule;
        } catch (IOException e) {
            log.error("Failed to read the antenatal contact schedule {}: {}", resourcePath,
                    e.getMessage(), e);
            return null;
        }
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        node.forEach(item -> out.add(item.asText()));
        return List.copyOf(out);
    }
}
