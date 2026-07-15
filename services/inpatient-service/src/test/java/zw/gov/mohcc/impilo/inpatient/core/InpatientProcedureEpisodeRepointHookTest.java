package zw.gov.mohcc.impilo.inpatient.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;
import zw.gov.mohcc.impilo.sharedkernel.identity.IdentityRepointCommand;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Wave 5b §15 — the {@code procedure_episode} identity-repoint hook. When a PROVISIONAL trauma patient is
 * reconciled ({@code vito.merge.executed}), the theatre record must follow the merge (subject_cpid moved
 * merged→survivor) so the surgery is not orphaned. Idempotent by construction; audited. The hook is
 * collected by the trauma lane's W6 fan-out consumer — here we exercise it directly.
 */
@ExtendWith(MockitoExtension.class)
class InpatientProcedureEpisodeRepointHookTest {

    @Mock ProcedureEpisodeRepository episodeRepository;
    @Mock EventOutboxRepository outboxRepository;

    private InpatientProcedureEpisodeRepointHook hook() {
        return new InpatientProcedureEpisodeRepointHook(episodeRepository, outboxRepository);
    }

    @Test
    void participantIsTheProcedureEpisodeTable() {
        assertEquals("inpatient.procedure_episode", hook().participant());
    }

    @Test
    void repointMovesTheatreCasesFromMergedToSurvivorAndAudits() {
        UUID tenant = UUID.randomUUID();
        String merged = "CPID-PROVISIONAL-UNKNOWN";
        String survivor = "CPID-ZW-RECONCILED";
        IdentityRepointCommand cmd = new IdentityRepointCommand(
                tenant.toString(), merged, survivor, "merge-1", "corr-1");
        when(episodeRepository.repointSubjectCpid(tenant, merged, survivor)).thenReturn(1);

        int moved = hook().repoint(cmd);

        assertEquals(1, moved, "the theatre case must follow the merge — no orphaned surgery");
        verify(episodeRepository).repointSubjectCpid(tenant, merged, survivor);
        // Audit row is keyed by the merge's idempotency key.
        ArgumentCaptor<EventOutboxEntity> audit = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(audit.capture());
        assertEquals(cmd.idempotencyKey(), audit.getValue().getIdempotencyKey());
        assertEquals("inpatient.procedure_episode.identity-repointed", audit.getValue().getEventType());
        assertTrue(audit.getValue().getPayloadJson().contains("\"rows_repointed\":1"));
    }

    @Test
    void redeliveredCommandRepointsZeroRows() {
        UUID tenant = UUID.randomUUID();
        String merged = "CPID-PROVISIONAL-UNKNOWN";
        String survivor = "CPID-ZW-RECONCILED";
        IdentityRepointCommand cmd = new IdentityRepointCommand(
                tenant.toString(), merged, survivor, "merge-1", "corr-1");
        // After the first apply, no subject_cpid still matches merged → 0 rows on redelivery.
        when(episodeRepository.repointSubjectCpid(tenant, merged, survivor)).thenReturn(0);

        assertEquals(0, hook().repoint(cmd), "a redelivered merge is a no-op (idempotent)");
        // Repointing zero rows is a legitimate outcome; the operation is still audited.
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void aMalformedTenantIsSkippedNotThrown() {
        IdentityRepointCommand cmd = new IdentityRepointCommand(
                "not-a-uuid", "a", "b", "merge-2", "corr-2");
        assertEquals(0, hook().repoint(cmd));
        verify(episodeRepository, never()).repointSubjectCpid(any(), any(), any());
    }
}
