package zw.gov.mohcc.impilo.learning.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.learning.fundo.FundoReplayMediaService;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;

/**
 * Consumes {@code impilo.live.replay.published.v1} (LEARNING_RECORDING W4) and
 * adopts the replay artifact into the Fundo media library.
 *
 * <p><strong>Layering law:</strong> learning-service NEVER consumes raw
 * {@code impilo.rtc.*} events — live-service owns the recording pipeline
 * (egress → document-service register-external) and republishes media truth as
 * {@code impilo.live.*}. This consumer joins that truth to learning sessions via
 * {@code lrn_scheduled_learning_session.live_event_id} (V027); live events with
 * no linked session are not ours and are skipped.</p>
 *
 * <p><strong>Envelope:</strong> live-service's outbox emitter wraps payloads as
 * {@code {"eventType":…, "aggregateType":…, "aggregateId":…, "occurredAt":…,
 * "payload":{…}}}; this consumer unwraps {@code payload} and tolerates flat
 * payloads defensively (same convention as {@link LiveAttendanceConsumer}).
 * The payload carries {@code eventId}, {@code documentObjectId} (the
 * document-service object id of the recording artifact), {@code sessionId},
 * {@code durationSeconds} and {@code title}.</p>
 *
 * <p>Errors are swallowed and logged so a poison message never kills the
 * listener (estate consumer convention). Idempotency lives in
 * {@link FundoReplayMediaService} (one asset per tenant + live event).</p>
 */
@Component
public class LiveReplayConsumer {

    private static final Logger log = LoggerFactory.getLogger(LiveReplayConsumer.class);

    private final ObjectMapper objectMapper;
    private final ScheduledLearningSessionRepository sessionRepository;
    private final FundoReplayMediaService replayMediaService;

    public LiveReplayConsumer(ObjectMapper objectMapper,
                              ScheduledLearningSessionRepository sessionRepository,
                              FundoReplayMediaService replayMediaService) {
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
        this.replayMediaService = replayMediaService;
    }

    @KafkaListener(
            topics = "${learning.live.replay-published-topic:impilo.live.replay.published.v1}",
            groupId = "${spring.kafka.consumer.group-id:learning-service}")
    public void onReplayPublished(String message) {
        try {
            JsonNode n = unwrap(message);
            UUID liveEventId = uuidOrNull(text(n, "eventId"));
            String documentObjectId = text(n, "documentObjectId");
            if (liveEventId == null) {
                log.debug("live replay.published without eventId — skipped");
                return;
            }
            if (documentObjectId == null) {
                // Pre-W1 replays (LiveKit room replay / legacy playback refs) carry no
                // catalogued artifact; there is nothing to adopt as recorded media.
                log.debug("live replay.published for event {} without documentObjectId — skipped",
                        liveEventId);
                return;
            }
            ScheduledLearningSessionEntity session = sessionRepository
                    .findFirstByLiveEventIdOrderByCreatedAtDesc(liveEventId)
                    .orElse(null);
            if (session == null) {
                // Standalone live event with no scheduled learning session — not ours.
                log.debug("live replay.published for unlinked event {} — skipped", liveEventId);
                return;
            }
            var asset = replayMediaService.adoptReplay(session,
                    new FundoReplayMediaService.ReplayPublished(
                            liveEventId, documentObjectId, intOrNull(n, "durationSeconds"),
                            text(n, "title")));
            log.info("Live replay adopted: event {} -> media asset {} (session {}, unattached={})",
                    liveEventId, asset.getId(), session.getId(), asset.isUnattached());
        } catch (Exception e) {
            // Never poison the listener: log and move on (estate consumer convention).
            log.warn("Failed to handle impilo.live replay.published event: {}", e.getMessage());
        }
    }

    private JsonNode unwrap(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        JsonNode payload = root.path("payload");
        return payload.isObject() ? payload : root;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static Integer intOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.asInt();
        }
        try {
            return Integer.parseInt(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID uuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
