package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Wave 5b §15 — PROVISIONAL-patient identity repoint for the theatre record. A later {@code /patients/merge}
 * → {@code vito.merge.executed} must repoint {@code procedure_episode.subject_cpid} off the merged
 * (provisional) CPID onto the surviving CPID so the surgery record is not orphaned.
 */
@ExtendWith(MockitoExtension.class)
class ProcedureEpisodeIdentityRepointServiceTest {

    @Mock ProcedureEpisodeRepository episodeRepository;

    private ProcedureEpisodeIdentityRepointService service() {
        return new ProcedureEpisodeIdentityRepointService(episodeRepository, new ObjectMapper());
    }

    @Test
    void mergeEventRepointsTheatreCasesFromMergedToSurvivorCpid() {
        UUID tenant = UUID.randomUUID();
        String merged = "CPID-PROVISIONAL-UNKNOWN";
        String survivor = "CPID-ZW-RECONCILED";
        when(episodeRepository.repointSubjectCpid(tenant, merged, survivor)).thenReturn(1);

        String payload = "{\"tenantId\":\"" + tenant + "\",\"survivor\":\"crid-1\",\"merged\":\"crid-2\","
                + "\"survivorHealthId\":\"" + survivor + "\",\"mergedHealthId\":\"" + merged + "\"}";

        int repointed = service().handleMergeEvent(payload);

        assertEquals(1, repointed);
        // The theatre record follows the merge — no orphaned surgery.
        verify(episodeRepository).repointSubjectCpid(tenant, merged, survivor);
    }

    @Test
    void aNoOpMergeIsIgnored() {
        // survivor == merged (nothing to repoint) → no DB write.
        String same = "CPID-SAME";
        String payload = "{\"tenantId\":\"" + UUID.randomUUID() + "\",\"survivorHealthId\":\"" + same
                + "\",\"mergedHealthId\":\"" + same + "\"}";

        int repointed = service().handleMergeEvent(payload);

        assertEquals(0, repointed);
        verify(episodeRepository, never()).repointSubjectCpid(any(), any(), any());
    }

    @Test
    void aMalformedEventIsSkippedNotRetriedForever() {
        assertEquals(0, service().handleMergeEvent("{ not json"));
        assertEquals(0, service().handleMergeEvent("{\"tenantId\":\"not-a-uuid\","
                + "\"survivorHealthId\":\"a\",\"mergedHealthId\":\"b\"}"));
        verify(episodeRepository, never()).repointSubjectCpid(any(), any(), any());
    }
}
