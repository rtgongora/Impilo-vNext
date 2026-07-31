package zw.gov.mohcc.impilo.experience.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The emergency lane's failure contract, in one place so it cannot be written two ways.
 *
 * <p>Two rules, and the class exists to make both structural rather than a convention someone
 * remembers:
 *
 * <ol>
 *   <li><b>A failed read is never an empty read.</b> When an upstream cannot answer, the response
 *       carries {@code error} + {@code message} and <b>no {@code data} key at all</b>. A panel that
 *       receives {@code {"data": []}} renders "no alerts"; a clinician reads that as "this patient
 *       has no alerts", which is the exact false-negative the register forbids. Omitting the key
 *       makes the honest rendering the only possible one — there is nothing empty to render.
 *   <li><b>The message must name what the absence is not.</b> Every message ends with
 *       "Do not treat this as an absence of &lt;subject&gt;." Callers pass the subject, not the
 *       sentence, so the clause cannot be dropped under time pressure.
 * </ol>
 *
 * <p>The envelope is deliberately <b>flat</b> ({@code {error, message, meta}}) — the shape the
 * honest-degradation register already uses in {@code StructuredHistoryController} and
 * {@code ClinicalTimelineController}. The ED lane's 31 routes still emit the nested
 * {@code HTTP_ERROR} envelope produced by {@code ResponseStatusException}; the shell's
 * {@code bff-errors.ts} reads both, and the ED migration is tracked separately.
 *
 * <p>Composite surfaces (the command board) do not fail whole: they return {@code items} plus a
 * {@code failures} list naming each source that could not be reached, so one dead upstream costs
 * one tile rather than the board.
 */
public final class EmergencyHonesty {

    private static final Logger log = LoggerFactory.getLogger(EmergencyHonesty.class);

    private EmergencyHonesty() {
    }

    /**
     * A source that could not be read. 502, flat envelope, no {@code data} key.
     *
     * @param code    snake_case, ends in {@code _unavailable} — the shell keys its copy off this
     * @param subject what the caller must not read the failure as an absence of, e.g. "emergency alerts"
     */
    public static ResponseEntity<Map<String, Object>> unavailable(String code, String subject, Throwable cause) {
        return unavailable(code, subject, cause, Map.of());
    }

    public static ResponseEntity<Map<String, Object>> unavailable(String code,
                                                                  String subject,
                                                                  Throwable cause,
                                                                  Map<String, Object> extraMeta) {
        // Logged at error, not warn: in this lane an unreadable source is a clinical safety event,
        // and a warn-level line is not what an on-call engineer is paged on.
        log.error("emergency source unavailable [{}]: {}", code, cause != null ? cause.getMessage() : "no cause");

        Map<String, Object> meta = new LinkedHashMap<>(extraMeta);
        meta.put("as_of", Instant.now().toString());
        meta.put("degraded", true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message(subject));
        body.put("meta", meta);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /** "&lt;Subject&gt; could not be retrieved. Do not treat this as an absence of &lt;subject&gt;." */
    public static String message(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException(
                    "an honest failure must name its subject — otherwise the clinician cannot tell "
                    + "what the missing answer was about");
        }
        String sentenceCase = Character.toUpperCase(subject.charAt(0)) + subject.substring(1);
        return sentenceCase + " could not be retrieved. Do not treat this as an absence of " + subject + ".";
    }

    /**
     * A composite board: whatever could be read, plus every source that could not, by name. Never a
     * 502 — a command board that disappears because one tile's upstream is down is worse than a
     * board that says which tile is blind.
     */
    public static final class Composite {

        private final Map<String, Object> items = new LinkedHashMap<>();
        private final List<Map<String, Object>> failures = new ArrayList<>();

        public Composite put(String key, Object value) {
            items.put(key, value);
            return this;
        }

        /** Record a source that could not be read. The board still renders; this tile states its limit. */
        public Composite failed(String key, String code, String subject, Throwable cause) {
            log.error("emergency board source unavailable [{}/{}]: {}", key, code,
                    cause != null ? cause.getMessage() : "no cause");
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("source", key);
            failure.put("error", code);
            failure.put("message", message(subject));
            failures.add(failure);
            return this;
        }

        public boolean isDegraded() {
            return !failures.isEmpty();
        }

        public ResponseEntity<Map<String, Object>> build() {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("as_of", Instant.now().toString());
            meta.put("degraded", isDegraded());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("items", items);
            body.put("failures", List.copyOf(failures));
            body.put("meta", meta);
            return ResponseEntity.ok(body);
        }
    }

    public static Composite composite() {
        return new Composite();
    }
}
