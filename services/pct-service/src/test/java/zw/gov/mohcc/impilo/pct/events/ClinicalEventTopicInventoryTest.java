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
 * the emit sites with:
 * <pre>grep -rn 'emit("pct\.' services/pct-service/src/main/java</pre>
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

    @org.junit.jupiter.api.Test
    @DisplayName("an event type is never listed in both maps")
    void noEventTypeIsBothRoutedAndOnTheCatchAll() {
        Set<String> overlap = new java.util.LinkedHashSet<>(ROUTED.keySet());
        overlap.retainAll(DELIBERATELY_ON_CATCH_ALL.keySet());
        assertTrue(overlap.isEmpty(), "listed twice with contradictory intent: " + overlap);
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
