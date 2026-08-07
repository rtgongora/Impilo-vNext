package zw.gov.mohcc.impilo.pct.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every clinical event type PCT emits, and where it is routed.
 *
 * <p><b>Why this exists.</b> {@code routeTopic} fails silently in the worst possible direction: an
 * event type with no case lands on the {@code pct.events} catch-all, the publish succeeds, the
 * outbox row is marked published, and every log line says the send worked. The only thing that does
 * not happen is the consumer hearing it. That is precisely how {@code pct.observation.recorded}
 * reached the shared health record never, while every step reported success — a defect that could
 * only be found by deploying.
 *
 * <p>So the omission is made visible here instead. Each event type PCT emits must be either
 * explicitly routed, or listed in {@link #DELIBERATELY_ON_CATCH_ALL} with the reason it has no
 * dedicated topic yet. Adding an emit without adding a line here fails the build, and a developer
 * writing a consumer can read this file to find out whether the event they want is actually
 * addressable.
 *
 * <p><b>Maintaining it:</b> the inventory is hand-listed rather than reflected out of the source,
 * because the point is that a human states the routing intent for a new cross-service event. Find
 * the emit sites with BOTH of these — an event can reach the outbox either way, and searching only
 * for {@code emit(} is how {@code pct.ed.critical_result} stayed absent from both maps while being
 * emitted in production:
 * <pre>
 * grep -rn 'emit("pct\.' services/pct-service/src/main/java
 * grep -rn 'setEventType(' services/pct-service/src/main/java
 * </pre>
 */
class ClinicalEventTopicInventoryTest {

    /**
     * Event type -> the topic a consumer must subscribe to. These are contracts: changing a value
     * here without changing the consumer breaks the link, and the break is invisible at runtime.
     */
    private static final Map<String, String> ROUTED = new LinkedHashMap<>() {{
        // BUTANO archives these into the shared health record as FHIR Observations.
        put("pct.observation.recorded", "pct.observation.recorded");

        // VITO asserts the mother-child relationship, UBOMI raises the civil birth notification
        // for a human to attest, BUTANO archives the birth summary.
        put("pct.newborn.episode.opened", "pct.newborn.episode.opened");
        put("pct.newborn.record.updated", "pct.newborn.episode.opened");

        // rito raises a safety signal and notification pages the responsible clinician. This event
        // was emitted by EdDiagnosticsService long before this inventory existed and appeared in
        // NEITHER map, so the guard silently passed while the estate's only ED safety event rode the
        // catch-all. See the maintenance note above: it is emitted via setEventType(), not emit().
        put("pct.ed.critical_result", "pct.emergency.critical_result");

        // BUTANO archives the structured examination as a FHIR ClinicalImpression (brief.md §19), so
        // the next facility sees not only the diagnosis but the regions nobody looked at.
        put("EXAMINATION_RECORDED", "pct.examination.recorded");

        // The emergency episode lifecycle. daidzai back-fills its continuum link from the state
        // change, butano archives it, reporting derives time-to-clinician and length-of-stay.
        put("EMERGENCY_EPISODE_OPENED", "pct.emergency.episode.state_changed");
        put("EMERGENCY_EPISODE_STATE_CHANGED", "pct.emergency.episode.state_changed");
        put("EMERGENCY_EPISODE_ANCHOR_RESOLVED", "pct.emergency.episode.state_changed");
        put("EMERGENCY_EPISODE_MERGED", "pct.emergency.episode.state_changed");
    }};

    /**
     * Emitted, and deliberately riding the catch-all because nothing consumes them across a service
     * boundary yet. Each is a candidate for its own topic the moment a consumer is written — and
     * writing that consumer means adding the {@code routeTopic} case in the same change, because the
     * route is the contract.
     */
    private static final Map<String, String> DELIBERATELY_ON_CATCH_ALL = new LinkedHashMap<>() {{
        put("pct.growth.recorded", "no cross-service consumer; growth is read from PCT directly");
        put("pct.immunization.recorded", "no cross-service consumer; the EPI forecast is stateless and reads PCT");
        put("pct.immunization.verified", "as above");
        put("pct.labour.observation.recorded", "labour observations are read from PCT; no consumer yet");
        put("pct.partograph.opened", "no consumer yet");
        put("pct.partograph.point.added", "no consumer yet");
        put("pct.partograph.closed", "no consumer yet");
        put("pct.ctg.session.opened", "no consumer yet");
        put("pct.ctg.chunk.recorded", "high volume; a consumer would need a dedicated topic and a retention policy");
        put("pct.ctg.annotation.recorded", "no consumer yet");
    }};

    /**
     * Emitted, unrouted, and until now recorded in neither map above.
     *
     * <p>Found 2026-08-07 while converting PCT to CompanionOutboxPublisher, by parsing every
     * {@code writeOutbox}/{@code setEventType} call in {@code src/main} and taking the set
     * difference against {@code routeTopic} and both maps here. Nineteen came back — this file's
     * own maintenance note predicted exactly this, since the inventory is hand-listed.</p>
     *
     * <p>They are kept apart from {@link #DELIBERATELY_ON_CATCH_ALL} on purpose. That map means
     * "considered, and the catch-all is right". This one means "nobody has decided yet", and
     * filing them as deliberate would be the tidy-looking fiction this file exists to prevent.</p>
     *
     * <p>{@code pct.events} is not quite a black hole, which is worse: its only subscriber is
     * {@code TelemedicineLifecycleConsumer} in experience-bff, looking for teleconsult events and
     * ignoring the rest. So each of these publishes successfully, marks its row published, logs a
     * send — and reaches nobody.</p>
     *
     * <p><b>The death-workflow block needs a routing decision, not a rubber stamp.</b> CRVS
     * package readiness, medico-legal flagging and a public-health signal are the kind of events
     * other services plausibly must hear. Routing them means naming a topic and a consumer, which
     * is a product decision rather than one to make while converting a publisher.</p>
     */
    private static final Map<String, String> FOUND_UNROUTED_PENDING_TRIAGE = new LinkedHashMap<>() {{
        // Death workflow — the block most likely to need real topics.
        put("DEATH_CONFIRMED", "PENDING: death registration; likely needs a consumer (UBOMI/CRVS)");
        put("DEATH_COMMUNITY_REPORTED", "PENDING: community death report; surveillance may need this");
        put("DEATH_REVIEW_REQUIRED", "PENDING: mortality review trigger");
        put("DEATH_MEDICO_LEGAL_FLAGGED", "PENDING: medico-legal referral");
        put("DEATH_PUBLIC_HEALTH_SIGNAL", "PENDING: surveillance signal — a consumer looks likely");
        put("DEATH_VERBAL_AUTOPSY", "PENDING: verbal autopsy captured");
        put("DEATH_FIELD_BODY_MANAGED", "PENDING: field body management");
        put("CRVS_PACKAGE_READY", "PENDING: civil registration package ready — UBOMI is the likely consumer");

        // Forms — read from PCT directly today; no cross-service consumer identified.
        put("FORM_RESPONSE_DRAFTED", "PENDING: no cross-service consumer identified");
        put("FORM_RESPONSE_SUBMITTED", "PENDING: no cross-service consumer identified");
        put("FORM_RESPONSE_AMENDED", "PENDING: no cross-service consumer identified");
        put("FORM_RESPONSE_COUNTERSIGNED", "PENDING: no cross-service consumer identified");
        put("FORM_RESPONSE_VOIDED", "PENDING: no cross-service consumer identified");
        put("FORM_RESOLUTION_RESOLVED", "PENDING: no cross-service consumer identified");

        // Remaining singletons.
        put("CADRE_DECISION_RESOLVED", "PENDING: no cross-service consumer identified");
        put("ENCOUNTER_PATHWAY_PROTOCOL_UPDATED", "PENDING: no cross-service consumer identified");
        put("IMAGING_LINK_CREATED", "PENDING: OROS/PACS may want this; not routed today");
        put("JOURNEY_SORTED", "PENDING: sorting desk outcome; no consumer identified");
        put("pct.observation.voided", "PENDING: pct.observation.recorded IS routed to BUTANO, so a "
                + "voided observation arguably must reach the same consumer to retract the record");
    }};

    @TestFactory
    @DisplayName("every emitted clinical event type is either routed or knowingly on the catch-all")
    List<DynamicTest> everyEmittedEventTypeStatesItsRoutingIntent() {
        return ROUTED.entrySet().stream()
                .map(e -> DynamicTest.dynamicTest(
                        e.getKey() + " -> " + e.getValue(),
                        () -> {
                            assertEquals(e.getValue(), OutboxPublisher.routeTopic(e.getKey()),
                                    e.getKey() + " must reach " + e.getValue()
                                            + " — a consumer subscribes to it there");
                            assertNotEquals("pct.events", OutboxPublisher.routeTopic(e.getKey()),
                                    e.getKey() + " has a declared consumer and must not fall to the catch-all");
                        }))
                .toList();
    }

    @TestFactory
    @DisplayName("catch-all events are listed on purpose, not forgotten")
    List<DynamicTest> catchAllEventsAreDeclared() {
        return DELIBERATELY_ON_CATCH_ALL.entrySet().stream()
                .map(e -> DynamicTest.dynamicTest(
                        e.getKey() + " (catch-all: " + e.getValue() + ")",
                        () -> {
                            assertTrue(e.getValue() != null && !e.getValue().isBlank(),
                                    "an event left on the catch-all must say why");
                            assertEquals("pct.events", OutboxPublisher.routeTopic(e.getKey()),
                                    e.getKey() + " is listed as riding the catch-all but is now routed. "
                                            + "If a consumer was added, move it to ROUTED and name the topic.");
                        }))
                .toList();
    }

    @TestFactory
    @DisplayName("events found unrouted are recorded, and still unrouted until someone decides")
    List<DynamicTest> unroutedEventsAreRecordedPendingTriage() {
        return FOUND_UNROUTED_PENDING_TRIAGE.entrySet().stream()
                .map(e -> DynamicTest.dynamicTest(
                        e.getKey() + " (pending: " + e.getValue() + ")",
                        () -> {
                            assertTrue(e.getValue() != null && e.getValue().startsWith("PENDING"),
                                    "an unrouted event must say that its routing is undecided");
                            assertEquals("pct.events", OutboxPublisher.routeTopic(e.getKey()),
                                    e.getKey() + " is recorded as awaiting a routing decision but is "
                                            + "now routed. If a consumer was added, move it to ROUTED "
                                            + "and name the topic.");
                        }))
                .toList();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("an event type is never listed in two maps with contradictory intent")
    void noEventTypeIsBothRoutedAndOnTheCatchAll() {
        Set<String> overlap = new java.util.LinkedHashSet<>(ROUTED.keySet());
        overlap.retainAll(DELIBERATELY_ON_CATCH_ALL.keySet());
        assertTrue(overlap.isEmpty(), "listed twice with contradictory intent: " + overlap);

        Set<String> pendingOverlap = new java.util.LinkedHashSet<>(FOUND_UNROUTED_PENDING_TRIAGE.keySet());
        pendingOverlap.retainAll(ROUTED.keySet());
        assertTrue(pendingOverlap.isEmpty(),
                "recorded as awaiting a routing decision while also declared routed: " + pendingOverlap);

        Set<String> deliberateOverlap = new java.util.LinkedHashSet<>(FOUND_UNROUTED_PENDING_TRIAGE.keySet());
        deliberateOverlap.retainAll(DELIBERATELY_ON_CATCH_ALL.keySet());
        assertTrue(deliberateOverlap.isEmpty(),
                "an event cannot be both a deliberate catch-all choice and an undecided one: "
                        + deliberateOverlap);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("a birth reaches the topic VITO, UBOMI and BUTANO can subscribe to")
    void birthEventsShareOneTopicSoOneSubscriptionSeesBothOpeningAndEnrichment() {
        // A replayed delivery event enriches an existing record rather than opening a new episode,
        // so it is emitted as .record.updated. A consumer that only heard .episode.opened would miss
        // every twin recorded second and every birth whose details arrived after the first write.
        assertEquals("pct.newborn.episode.opened", OutboxPublisher.routeTopic("pct.newborn.episode.opened"));
        assertEquals("pct.newborn.episode.opened", OutboxPublisher.routeTopic("pct.newborn.record.updated"));
    }
}
