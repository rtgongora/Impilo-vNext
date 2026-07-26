package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImamEpisodeRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raises the defaulter-tracing event from elapsed time rather than from someone noticing.
 *
 * <p>The visit path already emits {@code IMAM_TRACING_REQUIRED} when a review is recorded as
 * missed. Proving the feature on the estate showed the hole in relying on that alone: a child who
 * simply stops coming, and for whom nobody creates the missed-visit row, produces no event at all.
 * The clinic's worklist would still find them on a pull, but nothing would push — and the failure
 * this whole feature exists to prevent is a malnourished child nobody notices has stopped
 * attending. A trigger that only fires when a human notices is half a trigger.</p>
 *
 * <p>Fires once per crossing, not once per night. Moving from tracing into defaulting is new
 * information and raises a second event; repeating the same alert daily is what teaches a team to
 * stop reading them.</p>
 *
 * <p>Each child is banded by the schedule and thresholds copied onto their episode at enrolment,
 * so the sweep applies the governed rules they were admitted under and needs no clinical content
 * of its own.</p>
 */
@Component
public class ImamTracingSweep {

    public static final String SYSTEM_ACTOR = "system:imam-tracing-sweep";

    private static final Logger log = LoggerFactory.getLogger(ImamTracingSweep.class);

    private final ImamEpisodeRepository episodeRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public ImamTracingSweep(ImamEpisodeRepository episodeRepository,
                            EventOutboxRepository outboxRepository,
                            ObjectMapper objectMapper,
                            @Value("${pct.imam.tracing-sweep.enabled:true}") boolean enabled) {
        this.episodeRepository = episodeRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pct.imam.tracing-sweep.interval-ms:3600000}",
            initialDelayString = "${pct.imam.tracing-sweep.initial-delay-ms:120000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        int raised = raiseTracingEvents(LocalDate.now());
        if (raised > 0) {
            log.info("IMAM tracing sweep raised {} tracing event(s)", raised);
        }
    }

    /** @return the number of episodes that crossed a tracing threshold and were notified */
    @Transactional
    public int raiseTracingEvents(LocalDate asOf) {
        List<ImamEpisodeEntity> overdue = episodeRepository.findAllOverdue(asOf);
        int raised = 0;

        for (ImamEpisodeEntity episode : overdue) {
            LocalDate lastContact = episode.getLastContactOn() != null
                    ? episode.getLastContactOn()
                    : episode.getEnrolledAt().toLocalDate();
            int interval = episode.getReviewIntervalDays() == null || episode.getReviewIntervalDays() < 1
                    ? 7 : episode.getReviewIntervalDays();
            int grace = episode.getAttendanceGraceDays() == null ? 0 : episode.getAttendanceGraceDays();
            int daysSinceLastContact = (int) ChronoUnit.DAYS.between(lastContact, asOf);
            int missed = Math.max(0, (daysSinceLastContact - grace) / interval);

            if (missed < episode.getTraceAfterMissedVisits()) {
                continue;
            }
            int alreadyNotified = episode.getTracingNotifiedMissedVisits() == null
                    ? 0 : episode.getTracingNotifiedMissedVisits();
            if (missed <= alreadyNotified) {
                continue;
            }

            boolean defaulter = missed >= episode.getDefaulterMissedVisits();
            // An episode enrolled and never reviewed is the most serious row this sweep can find,
            // and it is the one a count of missed visits alone does not distinguish.
            boolean neverReviewed = episode.getLastContactOn() == null
                    || episode.getLastContactOn().equals(episode.getEnrolledAt().toLocalDate());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("imamEpisodeId", episode.getImamEpisodeId().toString());
            payload.put("subjectCpid", episode.getSubjectCpid());
            payload.put("programme", episode.getProgramme());
            payload.put("facilityId", episode.getFacilityId() == null ? "" : episode.getFacilityId().toString());
            payload.put("attendanceStatus", defaulter ? "DEFAULTER" : "TRACE_REQUIRED");
            payload.put("consecutiveMissedVisits", missed);
            payload.put("daysSinceLastContact", daysSinceLastContact);
            payload.put("lastContactOn", lastContact.toString());
            payload.put("nextReviewDue", episode.getNextReviewDue() == null
                    ? "" : episode.getNextReviewDue().toString());
            payload.put("neverReviewed", neverReviewed);
            payload.put("detectedBy", "ELAPSED_TIME");
            payload.put("actorId", SYSTEM_ACTOR);

            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("IMAM_EPISODE");
            outbox.setAggregateId(episode.getImamEpisodeId().toString());
            outbox.setEventType("IMAM_TRACING_REQUIRED");
            outbox.setPayload(toJson(payload));
            outbox.setTenantId(episode.getTenantId());
            outboxRepository.save(outbox);

            episode.setTracingNotifiedMissedVisits(missed);
            episode.setTracingNotifiedAt(OffsetDateTime.now());
            episode.setUpdatedAt(OffsetDateTime.now());
            episodeRepository.save(episode);
            raised++;

            log.info("pct.imam.tracing raised episode={} subject={} status={} missed={} neverReviewed={}",
                    episode.getImamEpisodeId(), episode.getSubjectCpid(),
                    defaulter ? "DEFAULTER" : "TRACE_REQUIRED", missed, neverReviewed);
        }
        return raised;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialise IMAM tracing payload: {}", e.getMessage());
            return "{}";
        }
    }
}
