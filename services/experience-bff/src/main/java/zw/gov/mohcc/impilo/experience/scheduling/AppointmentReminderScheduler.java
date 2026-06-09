package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Hourly scan for appointments ~24h ahead and dispatches reminder notifications.
 */
@Component
public class AppointmentReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderScheduler.class);

    private final BookingServiceClient bookingClient;
    private final AppointmentCommsWorkflowService commsWorkflow;
    private final boolean enabled;

    public AppointmentReminderScheduler(BookingServiceClient bookingClient,
                                        AppointmentCommsWorkflowService commsWorkflow,
                                        @Value("${impilo.scheduling.reminders.enabled:true}") boolean enabled) {
        this.bookingClient = bookingClient;
        this.commsWorkflow = commsWorkflow;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${impilo.scheduling.reminders.cron:0 15 * * * *}")
    public void dispatchReminders() {
        if (!enabled) {
            return;
        }
        try {
            List<JsonNode> appointments = new ArrayList<>();
            for (String status : List.of("CONFIRMED", "SCHEDULED")) {
                int page = 0;
                while (page < 20) {
                    JsonNode rows = bookingClient.listAppointments(null, null, status, page, 200);
                    List<JsonNode> batch = asList(rows);
                    if (batch.isEmpty()) {
                        break;
                    }
                    appointments.addAll(batch);
                    if (batch.size() < 200) {
                        break;
                    }
                    page++;
                }
            }
            int sent = commsWorkflow.dispatchUpcomingReminders(appointments);
            if (sent > 0) {
                log.info("Dispatched {} appointment reminder(s)", sent);
            }
        } catch (Exception ex) {
            log.warn("Appointment reminder scheduler failed: {}", ex.getMessage());
        }
    }

    private static List<JsonNode> asList(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node == null || node.isNull()) {
            return out;
        }
        if (node.isArray()) {
            node.forEach(out::add);
        } else {
            out.add(node);
        }
        return out;
    }
}
