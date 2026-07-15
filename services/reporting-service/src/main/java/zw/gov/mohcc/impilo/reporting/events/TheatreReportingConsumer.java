package zw.gov.mohcc.impilo.reporting.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.reporting.persistence.entity.RptTheatreCaseMetricEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.RptTheatreCaseMetricRepository;

import java.util.UUID;

/**
 * Wave 4 §20 — the FIRST downstream consumer of theatre.* events. Projects the theatre event stream
 * (legacy inpatient.events / inpatient.safety, where the message IS the raw payload map carrying
 * event_type + tenant_id) into the per-case metric table the theatre report catalog reads. Reports
 * therefore derive from REAL event data and reconcile with the cases actually run.
 */
@Component
public class TheatreReportingConsumer {

    private static final Logger log = LoggerFactory.getLogger(TheatreReportingConsumer.class);

    private final RptTheatreCaseMetricRepository metricRepository;
    private final ObjectMapper objectMapper;

    public TheatreReportingConsumer(RptTheatreCaseMetricRepository metricRepository, ObjectMapper objectMapper) {
        this.metricRepository = metricRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "#{'${reporting.kafka.topics.theatre:inpatient.events,inpatient.safety}'.split(',')}",
            groupId = "reporting-service")
    @Transactional
    public void consumeTheatreEvent(String message) {
        try {
            JsonNode e = objectMapper.readTree(message);
            String eventType = text(e, "event_type");
            if (eventType == null || !eventType.startsWith("theatre.")) return;
            String tenantId = text(e, "tenant_id");
            String episodeId = text(e, "episode_id");
            if (tenantId == null || episodeId == null) return;
            UUID tenant = parseUuid(tenantId);
            UUID episode = parseUuid(episodeId);
            if (tenant == null || episode == null) return;

            boolean tracked = switch (eventType) {
                case "theatre.case.intake", "theatre.case.booked", "theatre.case.started",
                     "theatre.case.completed", "theatre.case.cancelled",
                     "theatre.pacu.discharge-decision", "theatre.returned-to-theatre" -> true;
                default -> eventType.startsWith("theatre.safety.")
                        || "theatre.pacu.escalated".equals(eventType)
                        || "theatre.count.discrepancy".equals(eventType)
                        || "theatre.death.routed".equals(eventType);
            };
            if (!tracked) return;

            RptTheatreCaseMetricEntity m = metricRepository.findByTenantIdAndEpisodeId(tenant, episode)
                    .orElseGet(RptTheatreCaseMetricEntity::new);
            if (m.getId() == null) {
                m.setTenantId(tenant);
                m.setEpisodeId(episode);
            }
            String facility = text(e, "facility_id");
            if (facility != null && !facility.isBlank() && m.getFacilityId() == null) {
                m.setFacilityId(parseUuid(facility));
            }
            applyEvent(m, eventType, e);
            metricRepository.save(m);
        } catch (Exception ex) {
            // Reporting projection is best-effort — a malformed event must not stall the case pipeline.
            log.warn("reporting: failed to project theatre event (skipping): {}", ex.getMessage());
        }
    }

    private void applyEvent(RptTheatreCaseMetricEntity m, String eventType, JsonNode e) {
        switch (eventType) {
            case "theatre.case.intake", "theatre.case.booked", "theatre.case.started" -> {
                setProcedure(m, e);
                if (m.getStatus() == null) m.setStatus("SCHEDULED");
            }
            case "theatre.case.completed" -> {
                setProcedure(m, e);
                m.setStatus("COMPLETED");
                m.setTheatreMinutes(e.path("theatre_minutes").asInt(0));
                m.setHasAnaesthesia(e.path("has_anaesthesia").asBoolean(false));
                m.setImplantCount(e.path("implant_count").asInt(0));
                m.setConsumableCount(e.path("consumable_count").asInt(0));
                m.setBloodUnits(e.path("blood_units").asInt(0));
                m.setCompletedAt(java.time.OffsetDateTime.now());
            }
            case "theatre.case.cancelled" -> {
                m.setCancelled(true);
                m.setStatus("CANCELLED");
                m.setCancellationReason(text(e, "reason"));
            }
            case "theatre.pacu.discharge-decision" -> {
                m.setPacuDestination(text(e, "destination"));
                if (e.path("unplanned_icu").asBoolean(false)) m.setUnplannedIcu(true);
                if (m.getStatus() == null || "SCHEDULED".equals(m.getStatus())) m.setStatus("IN_RECOVERY");
            }
            case "theatre.returned-to-theatre" -> m.setReturnedToTheatre(true);
            default -> {
                // safety / escalation / count-discrepancy / death → complication signal
                m.setSafetyEventCount(m.getSafetyEventCount() + 1);
                m.setComplicationFlag(true);
            }
        }
    }

    private void setProcedure(RptTheatreCaseMetricEntity m, JsonNode e) {
        String code = text(e, "procedure_code");
        String name = text(e, "procedure_name");
        if (code != null && !code.isBlank()) m.setProcedureCode(code);
        if (name != null && !name.isBlank()) m.setProcedureName(name);
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private static UUID parseUuid(String s) {
        try { return UUID.fromString(s); } catch (RuntimeException e) { return null; }
    }
}
