package zw.gov.mohcc.impilo.reporting.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.reporting.persistence.entity.RptEmergencyEpisodeMetricEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.RptEmergencyEpisodeMetricRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * W17 — projects pct.emergency.* events into {@code rpt_emergency_episode_metric}.
 *
 * <p>Accepts both camelCase (PCT emit shape) and snake_case keys, and requires
 * {@code event_type} on the payload (PCT emitters now stamp it). Best-effort: a
 * malformed event logs and skips so the clinical pipeline is never stalled.
 */
@Component
public class EmergencyReportingConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmergencyReportingConsumer.class);

    private static final Set<String> CLOSED_STATES = Set.of(
            "CLOSED_HANDED_OVER",
            "CLOSED_DISCHARGED",
            "CLOSED_DIED",
            "CLOSED_LEFT_BEFORE_ASSESSMENT",
            "CLOSED_LEFT_AGAINST_ADVICE",
            "CLOSED_ABSCONDED",
            "CLOSED_DECLINED_CARE"
    );

    private final RptEmergencyEpisodeMetricRepository metricRepository;
    private final ObjectMapper objectMapper;

    public EmergencyReportingConsumer(
            RptEmergencyEpisodeMetricRepository metricRepository,
            ObjectMapper objectMapper) {
        this.metricRepository = metricRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "#{'${reporting.kafka.topics.emergency:pct.emergency.episode.state_changed,pct.emergency.alert.raised,pct.emergency.handover.updated,pct.emergency.identity.linked}'.split(',')}",
            groupId = "reporting-service-emergency")
    @Transactional
    public void consumeEmergencyEvent(String message) {
        try {
            JsonNode e = objectMapper.readTree(message);
            String eventType = text(e, "event_type", "eventType");
            if (eventType == null || eventType.isBlank()) return;

            UUID tenant = parseUuid(text(e, "tenant_id", "tenantId"));
            UUID episode = parseUuid(text(e, "episode_id", "episodeId"));
            if (tenant == null || episode == null) return;

            RptEmergencyEpisodeMetricEntity m = metricRepository.findByTenantIdAndEpisodeId(tenant, episode)
                    .orElseGet(RptEmergencyEpisodeMetricEntity::new);
            if (m.getId() == null) {
                m.setTenantId(tenant);
                m.setEpisodeId(episode);
            }

            String facility = text(e, "facility_id", "facilityId");
            if (facility != null && m.getFacilityId() == null) {
                m.setFacilityId(parseUuid(facility));
            }

            applyEvent(m, eventType, e);
            metricRepository.save(m);
        } catch (Exception ex) {
            log.warn("reporting: failed to project emergency event (skipping): {}", ex.getMessage());
        }
    }

    private void applyEvent(RptEmergencyEpisodeMetricEntity m, String eventType, JsonNode e) {
        if (eventType.startsWith("EMERGENCY_EPISODE_") || eventType.startsWith("pct.emergency.episode")) {
            applyEpisode(m, eventType, e);
            return;
        }
        if (eventType.startsWith("EMERGENCY_ALERT_") || eventType.contains("alert")) {
            applyAlert(m, eventType, e);
            return;
        }
        if (eventType.startsWith("EMERGENCY_HANDOVER_") || eventType.contains("handover")) {
            applyHandover(m, eventType, e);
            return;
        }
        if ("EMERGENCY_IDENTITY_LINKED".equals(eventType)) {
            String cpid = text(e, "subject_cpid", "subjectCpid");
            if (cpid != null) m.setSubjectCpid(cpid);
            String mode = text(e, "identity_mode", "identityMode");
            if (mode != null) m.setIdentityMode(mode);
        }
    }

    private void applyEpisode(RptEmergencyEpisodeMetricEntity m, String eventType, JsonNode e) {
        String ref = text(e, "episode_reference", "episodeReference");
        if (ref != null) m.setEpisodeReference(ref);
        String state = text(e, "state");
        if (state != null) m.setState(state);
        String previous = text(e, "previous_state", "previousState");
        if (previous != null) m.setPreviousState(previous);
        String route = text(e, "entry_route", "entryRoute");
        if (route != null) m.setEntryRoute(route);
        String cls = text(e, "episode_class", "episodeClass");
        if (cls != null) m.setEpisodeClass(cls);
        String mode = text(e, "identity_mode", "identityMode");
        if (mode != null) m.setIdentityMode(mode);
        String cpid = text(e, "subject_cpid", "subjectCpid");
        if (cpid != null) m.setSubjectCpid(cpid);
        String journey = text(e, "journey_id", "journeyId");
        if (journey != null) m.setJourneyId(journey);
        String encounter = text(e, "encounter_id", "encounterId");
        if (encounter != null) m.setEncounterId(encounter);
        String outcome = text(e, "outcome");
        if (outcome != null) {
            m.setOutcome(outcome);
            m.setDispositionType(outcome);
        }
        String acuity = text(e, "triage_acuity", "triageAcuity");
        if (acuity != null) m.setTriageAcuity(acuity);
        String system = text(e, "triage_system", "triageSystem");
        if (system != null) m.setTriageSystem(system);

        if ("EMERGENCY_EPISODE_OPENED".equals(eventType) && m.getOpenedAt() == null) {
            m.setOpenedAt(OffsetDateTime.now());
        }

        if (state != null && CLOSED_STATES.contains(state)) {
            m.setClosed(true);
            if (m.getClosedAt() == null) m.setClosedAt(OffsetDateTime.now());
            m.setDied("CLOSED_DIED".equals(state) || "DIED".equalsIgnoreCase(outcomeOrEmpty(m)));
            m.setLeftWithoutBeingSeen("CLOSED_LEFT_BEFORE_ASSESSMENT".equals(state));
            if (m.getOpenedAt() != null && m.getLengthOfStayMinutes() == null) {
                long minutes = java.time.Duration.between(m.getOpenedAt(), m.getClosedAt()).toMinutes();
                m.setLengthOfStayMinutes((int) Math.max(0, minutes));
            }
        }

        Integer ttt = intVal(e, "time_to_triage_minutes", "timeToTriageMinutes");
        if (ttt != null) m.setTimeToTriageMinutes(ttt);
        Integer los = intVal(e, "length_of_stay_minutes", "lengthOfStayMinutes");
        if (los != null) m.setLengthOfStayMinutes(los);

        OffsetDateTime opened = timestamp(e, "opened_at", "openedAt", "arrived_at", "arrivedAt");
        if (opened != null && m.getOpenedAt() == null) m.setOpenedAt(opened);
    }

    private void applyAlert(RptEmergencyEpisodeMetricEntity m, String eventType, JsonNode e) {
        if ("EMERGENCY_ALERT_RAISED".equals(eventType)) {
            m.setAlertCount(m.getAlertCount() + 1);
        }
        if ("EMERGENCY_ALERT_OVERDUE".equals(eventType)) {
            m.setAlertOverdueCount(m.getAlertOverdueCount() + 1);
        }
        String rito = text(e, "rito_case_ref", "ritoCaseRef");
        if (rito != null && !rito.isBlank()) m.setRitoCaseLinked(true);
    }

    private void applyHandover(RptEmergencyEpisodeMetricEntity m, String eventType, JsonNode e) {
        if ("EMERGENCY_HANDOVER_REQUESTED".equals(eventType)) {
            m.setHandoverCount(m.getHandoverCount() + 1);
        }
        if ("EMERGENCY_HANDOVER_EXPIRED".equals(eventType)) {
            m.setHandoverExpiredCount(m.getHandoverExpiredCount() + 1);
        }
        String rito = text(e, "rito_case_ref", "ritoCaseRef");
        if (rito != null && !rito.isBlank()) m.setRitoCaseLinked(true);
    }

    private static String outcomeOrEmpty(RptEmergencyEpisodeMetricEntity m) {
        return m.getOutcome() == null ? "" : m.getOutcome();
    }

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                String v = node.get(field).asText();
                if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v)) return v;
            }
        }
        return null;
    }

    private static Integer intVal(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull() && node.get(field).canConvertToInt()) {
                return node.get(field).asInt();
            }
        }
        return null;
    }

    private static OffsetDateTime timestamp(JsonNode node, String... fields) {
        String raw = text(node, fields);
        if (raw == null) return null;
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static UUID parseUuid(String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s.trim().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
