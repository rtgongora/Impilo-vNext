package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.core.ClientIdentityOperationsService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PctBirthRelationshipConsumerTest {

    private final ClientIdentityOperationsService service = mock(ClientIdentityOperationsService.class);
    private final PctBirthRelationshipConsumer consumer =
            new PctBirthRelationshipConsumer(service, new ObjectMapper());

    private final UUID tenant = UUID.randomUUID();
    private final UUID mother = UUID.randomUUID();
    private final UUID child = UUID.randomUUID();

    private String event(String outcome, String motherCpid) {
        String m = motherCpid == null ? "null" : "\"" + motherCpid + "\"";
        return "{\"tenantId\":\"" + tenant + "\",\"subjectCpid\":\"" + child + "\","
                + "\"motherCpid\":" + m + ",\"birthOutcome\":\"" + outcome + "\"}";
    }

    @Test
    void records_the_dyad_for_a_live_birth() {
        when(service.recordBirthDyad(any(), any(), any())).thenReturn(true);
        consumer.onNewbornEpisode(event("LIVE_BIRTH", mother.toString()));
        verify(service).recordBirthDyad(tenant, mother, child);
    }

    @Test
    void records_the_dyad_for_a_neonatal_death_a_baby_born_alive() {
        when(service.recordBirthDyad(any(), any(), any())).thenReturn(true);
        consumer.onNewbornEpisode(event("NEONATAL_DEATH", mother.toString()));
        verify(service).recordBirthDyad(tenant, mother, child);
    }

    @Test
    void skips_a_stillbirth_which_mints_no_living_identity() {
        consumer.onNewbornEpisode(event("STILLBIRTH", mother.toString()));
        verify(service, never()).recordBirthDyad(any(), any(), any());
    }

    @Test
    void skips_when_no_mother_is_on_the_event() {
        consumer.onNewbornEpisode(event("LIVE_BIRTH", null));
        verify(service, never()).recordBirthDyad(any(), any(), any());
    }

    @Test
    void an_unparseable_identifier_is_swallowed_not_retried() {
        String bad = "{\"tenantId\":\"" + tenant + "\",\"subjectCpid\":\"not-a-uuid\","
                + "\"motherCpid\":\"" + mother + "\",\"birthOutcome\":\"LIVE_BIRTH\"}";
        // Must not throw — a poison pill would wedge every subsequent birth.
        consumer.onNewbornEpisode(bad);
        verify(service, never()).recordBirthDyad(eq(tenant), any(), any());
    }
}
