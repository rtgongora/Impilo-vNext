package zw.gov.mohcc.impilo.sessiontemplates;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the bundled session-template documents and exposes them as an
 * immutable registry. FAIL-CLOSED: a missing or malformed document is a
 * construction-time error — consumers must not boot with partial doctrine.
 *
 * <p>No runtime state, no tenant data: templates version with code and ride
 * the same gates+deploy cycle as their interpreters (rtc-gateway token grants,
 * live-service guardrails, khuluma lobby behaviour, BFF join-flow hints).
 */
public final class SessionTemplateRegistry {

    private static final String RESOURCE_DIR = "/session-templates/";
    private static final Map<SessionMode, String> DOCUMENTS = Map.of(
            SessionMode.MEETING, "meeting.json",
            SessionMode.LIVE_EVENT, "live-event.json",
            SessionMode.LEARNING_LIVE, "learning-live.json",
            SessionMode.LEARNING_RECORDING, "learning-recording.json",
            SessionMode.TELEMEDICINE, "telemedicine.json");

    private final Map<SessionMode, SessionTemplate> templates;

    public SessionTemplateRegistry() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        Map<SessionMode, SessionTemplate> loaded = new EnumMap<>(SessionMode.class);
        for (Map.Entry<SessionMode, String> doc : DOCUMENTS.entrySet()) {
            String path = RESOURCE_DIR + doc.getValue();
            try (InputStream in = SessionTemplateRegistry.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("Session template missing from classpath: " + path);
                }
                SessionTemplate template = mapper.readValue(in, SessionTemplate.class);
                validate(doc.getKey(), template, path);
                loaded.put(doc.getKey(), template);
            } catch (IOException e) {
                throw new IllegalStateException("Session template unreadable: " + path, e);
            }
        }
        this.templates = Collections.unmodifiableMap(loaded);
    }

    private static void validate(SessionMode expected, SessionTemplate t, String path) {
        if (t.sessionMode() != expected) {
            throw new IllegalStateException(path + ": sessionMode " + t.sessionMode() + " != " + expected);
        }
        if (t.roles() == null || t.roles().isEmpty()) {
            throw new IllegalStateException(path + ": at least one role is required");
        }
        if (t.recording() == null || t.chat() == null || t.fallbackRules() == null || t.telemetry() == null) {
            throw new IllegalStateException(path + ": recording/chat/telemetry/fallbackRules are required");
        }
        if (t.tokenTtlSeconds() < 60) {
            throw new IllegalStateException(path + ": tokenTtlSeconds must be >= 60");
        }
        if (t.owningService() == null || t.owningService().isBlank()) {
            throw new IllegalStateException(path + ": owningService is required");
        }
    }

    /** The template for a mode — never null (all five modes load or nothing boots). */
    public SessionTemplate get(SessionMode mode) {
        SessionTemplate t = templates.get(mode);
        if (t == null) {
            throw new IllegalStateException("No template registered for " + mode);
        }
        return t;
    }

    /** Lenient wire lookup: null for unknown/absent mode strings. */
    public SessionTemplate find(String wireValue) {
        SessionMode mode = SessionMode.fromWire(wireValue);
        return mode == null ? null : templates.get(mode);
    }

    public List<SessionMode> modes() {
        return List.copyOf(templates.keySet());
    }
}
