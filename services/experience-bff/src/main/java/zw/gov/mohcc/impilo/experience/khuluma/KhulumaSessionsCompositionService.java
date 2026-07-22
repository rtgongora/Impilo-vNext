package zw.gov.mohcc.impilo.experience.khuluma;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

/**
 * Composes a citizen's "upcoming sessions" for the Khuluma hub from stable per-CPID readers —
 * booking appointments + pct patient telehealth. It CONSUMES existing endpoints only (no
 * teleconsult-orchestration coupling) and reports each source's ok/error independently, so a
 * failed or reshaped source degrades a single chip rather than breaking the hub or inventing data.
 */
@Service
public class KhulumaSessionsCompositionService {

    private final BookingServiceClient bookingClient;
    private final PctServiceClient pctClient;

    public KhulumaSessionsCompositionService(BookingServiceClient bookingClient, PctServiceClient pctClient) {
        this.bookingClient = bookingClient;
        this.pctClient = pctClient;
    }

    public Map<String, Object> upcomingFor(String cpid) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> sources = new ArrayList<>();

        try {
            for (JsonNode a : arr(bookingClient.listCitizenAppointments(cpid, null, 0, 20))) {
                items.add(item(text(a, "id", "appointmentId"), "APPOINTMENT",
                        firstNonBlank(text(a, "appointmentType", "type", "reason"), "Appointment"),
                        text(a, "startAt", "scheduledFor", "preferredDate", "appointmentDate"),
                        text(a, "status"), "/citizen/appointments", "booking"));
            }
            sources.add(source("booking", true, null));
        } catch (Exception e) {
            sources.add(source("booking", false, safe(e)));
        }

        try {
            for (JsonNode t : arr(pctClient.listPatientTelehealth(cpid))) {
                String id = text(t, "id", "sessionId", "referralId");
                items.add(item(id, "TELECONSULT", firstNonBlank(text(t, "title", "reason"), "Telehealth session"),
                        text(t, "startAt", "scheduledAt", "startTime", "scheduledFor"),
                        text(t, "status"), id.isEmpty() ? "/telemedicine" : "/telemedicine/session/" + id, "telehealth"));
            }
            sources.add(source("telehealth", true, null));
        } catch (Exception e) {
            sources.add(source("telehealth", false, safe(e)));
        }

        items.sort(Comparator.comparing(m -> String.valueOf(m.getOrDefault("startAt", ""))));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("sources", sources);
        return data;
    }

    private static Map<String, Object> item(String id, String kind, String title, String startAt,
                                            String status, String joinHref, String src) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("kind", kind);
        m.put("title", title);
        m.put("startAt", startAt);
        m.put("status", status);
        m.put("joinHref", joinHref);
        m.put("source", src);
        return m;
    }

    private static Map<String, Object> source(String name, boolean ok, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("ok", ok);
        m.put("error", error);
        return m;
    }

    private static List<JsonNode> arr(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node == null) return out;
        JsonNode a = node.isArray() ? node
                : node.has("data") ? node.get("data")
                : node.has("content") ? node.get("content")
                : node.has("items") ? node.get("items") : null;
        if (a != null && a.isArray()) a.forEach(out::add);
        return out;
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            if (n != null && n.hasNonNull(k) && !n.get(k).asText().isBlank()) return n.get(k).asText();
        }
        return "";
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private static String safe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
