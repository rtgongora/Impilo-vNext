package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deterministic state machine governing patient journey lifecycle transitions.
 *
 * <p>Every patient visit to a facility is modelled as a <em>journey</em> that
 * progresses through a well-defined set of states from arrival to discharge
 * (or other terminal outcomes). This state machine enforces the valid
 * transition graph, rejecting any transition that violates the allowed paths.</p>
 *
 * <h3>State Transition Graph</h3>
 * <pre>
 *   REGISTRATION_PENDING  -&gt;  ARRIVED
 *   ARRIVED               -&gt;  TRIAGED, QUEUED, CANCELLED, NO_SHOW
 *   TRIAGED               -&gt;  QUEUED, CANCELLED, NO_SHOW
 *   QUEUED                -&gt;  IN_SERVICE, NO_SHOW, LEFT_WITHOUT_BEING_SEEN, CANCELLED
 *   IN_SERVICE            -&gt;  ADMITTED, DISCHARGE_PENDING, QUEUED (re-queue), CANCELLED, DEATH_RECORDED
 *   ADMITTED              -&gt;  TRANSFERRED, DISCHARGE_PENDING, DEATH_RECORDED
 *   TRANSFERRED           -&gt;  ADMITTED, DISCHARGE_PENDING
 *   DISCHARGE_PENDING     -&gt;  DISCHARGED, ADMITTED, TRANSFERRED, DEATH_RECORDED, CANCELLED
 *   DEATH_RECORDED        -&gt;  (terminal)
 *   DISCHARGED            -&gt;  (terminal)
 *   CANCELLED             -&gt;  (terminal)
 *   NO_SHOW               -&gt;  (terminal)
 *   LEFT_WITHOUT_BEING_SEEN -&gt; (terminal)
 * </pre>
 *
 * <p>Each successful transition writes an outbox event for Kafka publication
 * and a telemetry event for operational analytics.</p>
 */
@Service
public class JourneyStateMachine {

    private static final Logger log = LoggerFactory.getLogger(JourneyStateMachine.class);

    /**
     * Crockford's Base32 alphabet used for ULID generation.
     */
    private static final char[] CROCKFORD_BASE32 =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /**
     * Immutable map of allowed state transitions.
     * Key = current state, Value = set of valid target states.
     */
    private static final Map<JourneyState, Set<JourneyState>> TRANSITIONS;

    static {
        Map<JourneyState, Set<JourneyState>> m = new EnumMap<>(JourneyState.class);

        m.put(JourneyState.REGISTRATION_PENDING,
                EnumSet.of(JourneyState.ARRIVED));

        m.put(JourneyState.ARRIVED,
                EnumSet.of(JourneyState.TRIAGED, JourneyState.QUEUED,
                        JourneyState.CANCELLED, JourneyState.NO_SHOW));

        m.put(JourneyState.TRIAGED,
                EnumSet.of(JourneyState.QUEUED,
                        JourneyState.CANCELLED, JourneyState.NO_SHOW));

        m.put(JourneyState.QUEUED,
                EnumSet.of(JourneyState.IN_SERVICE, JourneyState.NO_SHOW,
                        JourneyState.LEFT_WITHOUT_BEING_SEEN, JourneyState.CANCELLED));

        m.put(JourneyState.IN_SERVICE,
                EnumSet.of(JourneyState.ADMITTED, JourneyState.DISCHARGE_PENDING,
                        JourneyState.QUEUED, JourneyState.CANCELLED,
                        JourneyState.DEATH_RECORDED));

        m.put(JourneyState.ADMITTED,
                EnumSet.of(JourneyState.TRANSFERRED, JourneyState.DISCHARGE_PENDING,
                        JourneyState.DEATH_RECORDED));

        m.put(JourneyState.TRANSFERRED,
                EnumSet.of(JourneyState.ADMITTED, JourneyState.DISCHARGE_PENDING));

        // DISCHARGE_PENDING can reach any terminal disposition state.
        // The specific target is determined by the discharge type:
        //   CLINICAL → DISCHARGED, ADMIT → ADMITTED, TRANSFER → TRANSFERRED,
        //   DEATH → DEATH_RECORDED, or CANCELLED (cancel discharge)
        m.put(JourneyState.DISCHARGE_PENDING,
                EnumSet.of(JourneyState.DISCHARGED, JourneyState.ADMITTED,
                        JourneyState.TRANSFERRED, JourneyState.DEATH_RECORDED,
                        JourneyState.CANCELLED));

        // Terminal states have no outgoing transitions
        m.put(JourneyState.DEATH_RECORDED, EnumSet.noneOf(JourneyState.class));
        m.put(JourneyState.DISCHARGED, EnumSet.noneOf(JourneyState.class));
        m.put(JourneyState.CANCELLED, EnumSet.noneOf(JourneyState.class));
        m.put(JourneyState.NO_SHOW, EnumSet.noneOf(JourneyState.class));
        m.put(JourneyState.LEFT_WITHOUT_BEING_SEEN, EnumSet.noneOf(JourneyState.class));

        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    private final JourneyRepository journeyRepository;
    private final EventOutboxRepository outboxRepository;
    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    public JourneyStateMachine(JourneyRepository journeyRepository,
                               EventOutboxRepository outboxRepository,
                               TelemetryService telemetryService,
                               ObjectMapper objectMapper) {
        this.journeyRepository = journeyRepository;
        this.outboxRepository = outboxRepository;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Transition a journey to the specified target state.
     *
     * <p>Validates that the transition from the journey's current state to
     * {@code targetState} is permitted by the transition graph. If valid,
     * the state is updated, the journey is persisted, and both an outbox
     * event and a telemetry event are written within the same transaction.</p>
     *
     * @param journey     the journey entity to transition (must be managed/attached)
     * @param targetState the desired target state
     * @return the updated journey entity
     * @throws IllegalStateException if the transition is not allowed
     */
    @Transactional
    public JourneyEntity transition(JourneyEntity journey, JourneyState targetState) {
        JourneyState currentState = journey.getState();

        Set<JourneyState> allowed = TRANSITIONS.getOrDefault(currentState, EnumSet.noneOf(JourneyState.class));
        if (!allowed.contains(targetState)) {
            throw new IllegalStateException(String.format(
                    "Invalid journey transition: %s -> %s for journey %s. Allowed targets from %s: %s",
                    currentState, targetState, journey.getJourneyId(), currentState, allowed));
        }

        JourneyState previousState = currentState;
        journey.setState(targetState);
        journey.setUpdatedAt(OffsetDateTime.now());
        journey = journeyRepository.save(journey);

        // Outbox event for downstream consumers
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("journeyId", journey.getJourneyId());
        payload.put("patientCpid", journey.getPatientCpid());
        payload.put("facilityId", journey.getFacilityId().toString());
        payload.put("previousState", previousState.name());
        payload.put("newState", targetState.name());
        payload.put("transitionedAt", OffsetDateTime.now().toString());
        payload.put("actorId", TrustContextHolder.require().actorId());
        writeOutbox("JOURNEY", journey.getJourneyId(), "JOURNEY_STATE_CHANGED", toJson(payload));

        // Telemetry event for analytics
        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("previousState", previousState.name());
        telemetry.put("newState", targetState.name());
        telemetryService.record("journey.state.changed", journey.getJourneyId(), telemetry);

        log.info("Journey {} transitioned: {} -> {}", journey.getJourneyId(), previousState, targetState);

        return journey;
    }

    /**
     * Create a new patient journey with state {@link JourneyState#ARRIVED}.
     *
     * <p>Generates a ULID-based journey identifier for global uniqueness and
     * chronological sortability. The journey is persisted and an outbox event
     * is published for downstream subscribers.</p>
     *
     * @param facilityId     the facility where the patient arrived
     * @param patientCpid    the patient's CPID (community patient identifier)
     * @param referralSource the source of the referral (e.g. SELF, FACILITY, COMMUNITY); may be {@code null}
     * @param referralId     an external referral identifier; may be {@code null}
     * @return the newly created journey entity in ARRIVED state
     */
    @Transactional
    public JourneyEntity createJourney(UUID facilityId, String patientCpid,
                                       String referralSource, String referralId) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = new JourneyEntity();
        journey.setJourneyId(generateUlid());
        journey.setTenantId(ctx.tenantId());
        journey.setFacilityId(facilityId);
        journey.setPatientCpid(patientCpid);
        journey.setState(JourneyState.ARRIVED);
        journey.setReferralSource(referralSource);
        journey.setReferralId(referralId);
        journey.setCreatedAt(OffsetDateTime.now());
        journey.setUpdatedAt(OffsetDateTime.now());

        journey = journeyRepository.save(journey);

        // Outbox event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("journeyId", journey.getJourneyId());
        payload.put("patientCpid", patientCpid);
        payload.put("facilityId", facilityId.toString());
        payload.put("state", JourneyState.ARRIVED.name());
        payload.put("referralSource", referralSource);
        payload.put("referralId", referralId);
        payload.put("createdAt", journey.getCreatedAt().toString());
        writeOutbox("JOURNEY", journey.getJourneyId(), "JOURNEY_CREATED", toJson(payload));

        log.info("Journey created: id={}, patient={}, facility={}",
                journey.getJourneyId(), patientCpid, facilityId);

        return journey;
    }

    // ------------------------------------------------------------------
    // ULID generation (no external library)
    // ------------------------------------------------------------------

    /**
     * Generate a ULID (Universally Unique Lexicographically Sortable Identifier).
     *
     * <p>Encodes the current millisecond timestamp in the first 10 characters
     * (Crockford's Base32) and fills the remaining 16 characters with
     * cryptographically-adequate randomness from {@link ThreadLocalRandom}.</p>
     *
     * @return a 26-character ULID string
     */
    static String generateUlid() {
        long timestamp = System.currentTimeMillis();
        char[] chars = new char[26];

        // Encode 48-bit timestamp in first 10 characters (most-significant first)
        for (int i = 9; i >= 0; i--) {
            chars[i] = CROCKFORD_BASE32[(int) (timestamp & 0x1F)];
            timestamp >>>= 5;
        }

        // Encode 80-bit random component in remaining 16 characters
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 10; i < 26; i++) {
            chars[i] = CROCKFORD_BASE32[random.nextInt(32)];
        }

        return new String(chars);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(TrustContextHolder.require().tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise outbox payload: {}", e.getMessage());
            return "{}";
        }
    }
}
