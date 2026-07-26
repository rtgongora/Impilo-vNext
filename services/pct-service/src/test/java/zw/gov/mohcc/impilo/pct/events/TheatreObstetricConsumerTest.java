package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.core.clinical.NewbornEpisodeService;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TheatreObstetricConsumerTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private NewbornEpisodeService newbornEpisodeService;

    private TheatreObstetricConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TheatreObstetricConsumer(new ObjectMapper(), newbornEpisodeService);
    }

    @Test
    void aDeliveryEventOpensTheNewbornEpisode() {
        consumer.onTheatreEvent("""
                {
                  "event_type": "theatre.obstetric.delivery",
                  "tenant_id": "%s",
                  "payload": {
                    "episode_id": "EP-1",
                    "mother_cpid": "CPID-MOTHER-1",
                    "baby_cpid": "CPID-BABY-1",
                    "outcome": "LIVE_BIRTH",
                    "apgar_5": "9"
                  }
                }
                """.formatted(TENANT));

        Map<String, Object> body = captureBody();
        assertThat(body.get("subject_cpid")).isEqualTo("CPID-BABY-1");
        assertThat(body.get("mother_cpid")).isEqualTo("CPID-MOTHER-1");
        assertThat(body.get("delivery_source")).isEqualTo("THEATRE");
        assertThat(body.get("delivery_source_ref")).isEqualTo("EP-1");
        assertThat(body.get("outcome")).isEqualTo("LIVE_BIRTH");
    }

    @Test
    void aNeonatalHandoverCarriesTheDestinationOntoTheRecord() {
        consumer.onTheatreEvent("""
                {
                  "event_type": "theatre.obstetric.neonatal-handover",
                  "tenant_id": "%s",
                  "payload": {
                    "episode_id": "EP-1",
                    "baby_cpid": "CPID-BABY-1",
                    "mother_cpid": "CPID-MOTHER-1",
                    "destination": "NICU"
                  }
                }
                """.formatted(TENANT));

        assertThat(captureBody().get("destination")).isEqualTo("NICU");
    }

    @Test
    void aLegacyPayloadWithoutAnEnvelopeIsStillRecognisedAsADelivery() {
        // The legacy emission publishes the bare payload with no event_type wrapper.
        consumer.onTheatreEvent("""
                {
                  "tenant_id": "%s",
                  "episode_id": "EP-9",
                  "mother_cpid": "CPID-MOTHER-9",
                  "baby_cpid": "CPID-BABY-9",
                  "outcome": "LIVE_BIRTH"
                }
                """.formatted(TENANT));

        assertThat(captureBody().get("subject_cpid")).isEqualTo("CPID-BABY-9");
    }

    @Test
    void unrelatedTheatreTrafficOnTheSharedTopicIsIgnored() {
        consumer.onTheatreEvent("""
                {
                  "event_type": "theatre.case.intake",
                  "tenant_id": "%s",
                  "payload": {"episode_id": "EP-2", "subject_cpid": "CPID-ADULT-1"}
                }
                """.formatted(TENANT));

        verify(newbornEpisodeService, never()).ensureNewbornEpisode(anyMap());
    }

    @Test
    void anEventWithoutABabyIdentityIsSkippedRatherThanGuessed() {
        consumer.onTheatreEvent("""
                {
                  "event_type": "theatre.obstetric.delivery",
                  "tenant_id": "%s",
                  "payload": {"episode_id": "EP-3", "mother_cpid": "CPID-MOTHER-3", "outcome": "LIVE_BIRTH"}
                }
                """.formatted(TENANT));

        verify(newbornEpisodeService, never()).ensureNewbornEpisode(anyMap());
    }

    @Test
    void anEventWithoutATenantIsSkippedBecauseTheRecordCouldNotBeScopedSafely() {
        consumer.onTheatreEvent("""
                {
                  "event_type": "theatre.obstetric.delivery",
                  "payload": {"baby_cpid": "CPID-BABY-4", "outcome": "LIVE_BIRTH"}
                }
                """);

        verify(newbornEpisodeService, never()).ensureNewbornEpisode(anyMap());
    }

    @Test
    void malformedOrEmptyMessagesDoNotBreakTheConsumer() {
        consumer.onTheatreEvent(null);
        consumer.onTheatreEvent("");
        consumer.onTheatreEvent("not json at all");

        verify(newbornEpisodeService, never()).ensureNewbornEpisode(anyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureBody() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(newbornEpisodeService).ensureNewbornEpisode(captor.capture());
        return captor.getValue();
    }
}
