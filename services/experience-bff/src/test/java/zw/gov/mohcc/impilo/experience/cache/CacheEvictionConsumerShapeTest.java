package zw.gov.mohcc.impilo.experience.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Cache eviction is the one BFF consumer that changes state rather than logging, so a field
 * it fails to read is a stale cache, not a blank log line.
 *
 * <p>Two independent things have to line up: the wire shape (raw payload vs v1.1 envelope)
 * and the naming convention (producers write camelCase, these listeners ask for snake_case).
 * Both were untested, and both were invisible because the topics involved had no producer —
 * {@code pct.journey} never existed at all, since PCT routes journey events to
 * {@code pct.journey.state_changed}. Converting PCT gives {@code impilo.pct.journey} a
 * producer for the first time.</p>
 */
@DisplayName("CacheEvictionConsumer reads both wire shapes and both namings")
class CacheEvictionConsumerShapeTest {

    private CacheService cacheService;
    private CacheEvictionConsumer consumer;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        consumer = new CacheEvictionConsumer(cacheService, new ObjectMapper());
    }

    @Test
    @DisplayName("a raw journey payload in camelCase evicts the facility queue")
    void rawCamelCaseJourneyEvicts() {
        // Exactly what PCT's JourneyStateMachine writes today.
        consumer.onJourneyEvent("""
                {"journeyId":"J-1","patientCpid":"CPID-1","facilityId":"FAC-1",\
                "previousState":"ARRIVED","newState":"IN_CONSULT"}""");

        verify(cacheService).evictPattern("queue:FAC-1:*");
    }

    @Test
    @DisplayName("the same payload inside a v1.1 envelope evicts identically")
    void envelopedCamelCaseJourneyEvicts() {
        consumer.onJourneyEvent("""
                {"event_id":"e-1","event_type":"impilo.pct.journey.state_changed.v1",\
                "tenant_id":"t-1","pod_id":"national","subject_type":"JOURNEY",\
                "subject_id":"J-1",\
                "payload":{"journeyId":"J-1","facilityId":"FAC-1","newState":"IN_CONSULT"},\
                "meta":{}}""");

        verify(cacheService).evictPattern("queue:FAC-1:*");
    }

    @Test
    @DisplayName("snake_case still works — the fix is additive, not a swap")
    void snakeCaseStillEvicts() {
        consumer.onJourneyEvent("{\"encounter_id\":\"ENC-1\",\"facility_id\":\"FAC-2\"}");

        verify(cacheService).evict("encounter:ENC-1:active");
        verify(cacheService).evictPattern("queue:FAC-2:*");
    }

    @Test
    @DisplayName("an enveloped facility event evicts by subject_id when the payload omits it")
    void facilityEventFallsBackToSubjectId() {
        consumer.onFacilityEvent("""
                {"event_id":"e-2","subject_id":"FAC-9","payload":{"name":"Parirenyatwa"},"meta":{}}""");

        verify(cacheService).evictPattern("facility:FAC-9:*");
    }

    @Test
    @DisplayName("a journey payload carrying no facility evicts nothing rather than a wrong key")
    void noFacilityEvictsNothing() {
        consumer.onJourneyEvent("{\"journeyId\":\"J-2\",\"newState\":\"IN_CONSULT\"}");

        verify(cacheService, never()).evictPattern(anyString());
        verify(cacheService, never()).evict(anyString());
    }

    @Test
    @DisplayName("a malformed message is swallowed, not propagated into the listener")
    void malformedMessageIsInert() {
        consumer.onJourneyEvent("not json at all");

        verify(cacheService, never()).evictPattern(anyString());
        verify(cacheService, never()).evict(anyString());
    }
}
