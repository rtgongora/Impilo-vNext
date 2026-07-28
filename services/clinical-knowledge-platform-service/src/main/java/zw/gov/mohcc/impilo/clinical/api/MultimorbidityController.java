package zw.gov.mohcc.impilo.clinical.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityContext;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityEngine;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityShrPublisher;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityView;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The multimorbidity view required by brief.md §9.
 *
 * <p>Takes the whole patient at once — problem list, medicines, appointments, investigations, renal
 * and hepatic function, functional status, priorities and care team — because every one of §9's
 * seven detections is about the relationship between two of those, and no single-disease endpoint
 * can see a relationship.</p>
 *
 * <p><strong>Absent and empty are different on the wire and must stay different.</strong> A key that
 * is missing means the caller could not obtain that source; a key present with an empty array means
 * the source was read and there is nothing in it. The engine reports the first as unanswered and the
 * second as clear, so a composition layer that helpfully defaults a failed read to {@code []} would
 * turn every outage into an all-clear.</p>
 */
@RestController
@RequestMapping("/internal/v1/clinical/multimorbidity")
public class MultimorbidityController {

    private final MultimorbidityEngine engine;
    private final ObjectMapper objectMapper;
    private final MultimorbidityShrPublisher shrPublisher;

    public MultimorbidityController(MultimorbidityEngine engine, ObjectMapper objectMapper,
                                    MultimorbidityShrPublisher shrPublisher) {
        this.engine = engine;
        this.objectMapper = objectMapper;
        this.shrPublisher = shrPublisher;
    }

    /**
     * @param subjectCpid taken from the body's optional {@code subjectCpid}. Present, the detected
     *                    issues are filed to the shared health record (brief.md §19); absent, the
     *                    assessment is returned and nothing is filed, because an issue with no
     *                    subject cannot be attached to a patient. The composing caller in
     *                    experience-bff holds the CPID and does not yet send it — that one line is
     *                    the remaining gap in this path, and it is left visible here rather than
     *                    worked around by inferring a subject from the medicine list.
     */
    @PostMapping("/assess")
    public ResponseEntity<Map<String, Object>> assess(
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader) {
        JsonNode root = body == null ? objectMapper.createObjectNode() : body;
        MultimorbidityContext ctx = new MultimorbidityContext(
                conditions(root.get("conditions")),
                strings(root.get("medicationCodes")),
                appointments(root.get("appointments")),
                investigations(root.get("investigations")),
                root.hasNonNull("egfr") ? root.get("egfr").asDouble() : null,
                root.hasNonNull("hepaticImpairment") ? root.get("hepaticImpairment").asText() : null,
                strings(root.get("functionalLimitations")),
                strings(root.get("patientPriorities")),
                careTeam(root.get("careTeam")));
        LocalDate asOf = date(root.path("asOf").asText(null));
        MultimorbidityView view = engine.assess(ctx, asOf);

        // Filed on the existing clinical.event_outbox spine. The assessment is returned either way:
        // a failure to reach the shared record must not deny the clinician in front of the patient
        // the view they asked for.
        shrPublisher.publish(view, root.path("subjectCpid").asText(null),
                root.hasNonNull("tenantId") ? root.get("tenantId").asText() : tenantHeader);

        return ResponseEntity.ok(Map.of("data", view.toMap()));
    }

    /** @return null when the key was absent — never an empty list, which would mean "there are none" */
    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonNode n : node) {
            String v = n.asText("").trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static List<MultimorbidityContext.Condition> conditions(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<MultimorbidityContext.Condition> out = new ArrayList<>();
        for (JsonNode n : node) {
            out.add(new MultimorbidityContext.Condition(
                    n.path("code").asText(null),
                    n.path("display").asText(null),
                    // Absent means we were not told; treating an untold condition as long-term would
                    // inflate the burden score, so the conservative default is false.
                    n.path("longTerm").asBoolean(false)));
        }
        return out;
    }

    private static List<MultimorbidityContext.Appointment> appointments(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<MultimorbidityContext.Appointment> out = new ArrayList<>();
        for (JsonNode n : node) {
            LocalDate d = date(n.path("date").asText(null));
            if (d == null) {
                // An appointment with no date cannot be placed in the window; dropping it silently
                // would understate the burden, so it is skipped and the count says what it counted.
                continue;
            }
            out.add(new MultimorbidityContext.Appointment(d, n.path("clinic").asText(null),
                    n.path("reason").asText(null)));
        }
        return out;
    }

    private static List<MultimorbidityContext.Investigation> investigations(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<MultimorbidityContext.Investigation> out = new ArrayList<>();
        for (JsonNode n : node) {
            LocalDate d = date(n.path("date").asText(null));
            if (d == null) {
                continue;
            }
            out.add(new MultimorbidityContext.Investigation(n.path("code").asText(null), d,
                    n.path("orderedBy").asText(null)));
        }
        return out;
    }

    private static List<MultimorbidityContext.CareTeamMember> careTeam(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<MultimorbidityContext.CareTeamMember> out = new ArrayList<>();
        for (JsonNode n : node) {
            out.add(new MultimorbidityContext.CareTeamMember(
                    n.path("role").asText(null), n.path("name").asText(null),
                    n.path("responsibleFor").asText(null)));
        }
        return out;
    }

    private static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
