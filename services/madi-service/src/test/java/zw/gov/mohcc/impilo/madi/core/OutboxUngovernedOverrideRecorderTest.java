package zw.gov.mohcc.impilo.madi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverrideRecorder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * The audit write itself, against the real emitter and a captured outbox row.
 *
 * <h2>Why this exists separately from EmergencyAccessGuardTest</h2>
 * <p>That test asserts the guard <em>calls</em> a recorder, using a lambda. It would pass against a
 * recorder that did nothing. The brief for this work put it exactly right — an allow-without-audit
 * passes a naive test and is precisely the ungoverned override this is meant to make visible — and
 * the same trap exists one level down: asserting the call, not the write.</p>
 *
 * <p>So this builds the real {@link MadiEventEmitter} over a mocked repository and asserts the row
 * that would actually be persisted.</p>
 *
 * <h2>On the write path workstream A guarded</h2>
 * <p>Workstream A added authorization to tshepo-audit's ingest and audit-ledger's append, which
 * raised the question of whether this caller can still write. It structurally does not arise: this
 * record goes to MADI's own outbox — a local insert in the caller's transaction — and touches
 * neither service. That is deliberate, because this record is written precisely when remote calls
 * are failing, and a shared fault would otherwise lose the record for the same reason it lost the
 * grant check.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxUngovernedOverrideRecorderTest {

    @Mock
    private EventOutboxRepository outboxRepository;

    private static final UUID TENANT = UUID.randomUUID();

    private OutboxUngovernedOverrideRecorder recorder() {
        return new OutboxUngovernedOverrideRecorder(
                new MadiEventEmitter(outboxRepository, new ObjectMapper()));
    }

    private static UngovernedOverrideRecorder.UngovernedOverride override(String tenantId) {
        return new UngovernedOverrideRecorder.UngovernedOverride(
                tenantId, "clinician-7", "O_NEG_EMERGENCY_RELEASE", "CPID-42",
                "BREAK_GLASS", "grant-1", "trust plane unreachable; requires post-hoc review");
    }

    private EventOutboxEntity captureSavedRow(String tenantId) {
        recorder().record(override(tenantId));
        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    /** The row must actually be written — not merely the recorder invoked. */
    @Test
    void anOverrideIsPersistedToTheOutbox() {
        assertNotNull(captureSavedRow(TENANT.toString()));
    }

    /**
     * The review is a selection on event type, not a scan of access logs. If this pair changes, the
     * review query silently returns nothing and the overrides become invisible — which is the
     * failure this whole branch exists to prevent.
     */
    @Test
    void theRowIsLegibleAsAnUngovernedOverrideAndNotAsAnAccessLogLine() {
        EventOutboxEntity row = captureSavedRow(TENANT.toString());

        assertEquals("BREAK_GLASS_UNGOVERNED_OVERRIDE", row.getEventType());
        assertEquals("EMERGENCY_ACCESS", row.getAggregateType());
        assertEquals(OutboxUngovernedOverrideRecorder.EVENT_TYPE, row.getEventType());
        assertEquals(OutboxUngovernedOverrideRecorder.AGGREGATE_TYPE, row.getAggregateType());
    }

    /** The review obligation travels in the record, so it cannot be inferred away later. */
    @Test
    void theRowCarriesTheReviewObligationAndTheUngovernedFlag() {
        String json = captureSavedRow(TENANT.toString()).getPayloadJson();

        assertTrue(json.contains("\"reviewRequired\":true"), json);
        assertTrue(json.contains("\"reviewStatus\":\"PENDING\""), json);
        assertTrue(json.contains("\"governed\":false"), json);
    }

    /** Everything a reviewer needs to judge the override without another lookup. */
    @Test
    void theRowCarriesWhoDidWhatToWhomAndWhy() {
        String json = captureSavedRow(TENANT.toString()).getPayloadJson();

        assertTrue(json.contains("clinician-7"), json);
        assertTrue(json.contains("O_NEG_EMERGENCY_RELEASE"), json);
        assertTrue(json.contains("CPID-42"), json);
        assertTrue(json.contains("BREAK_GLASS"), json);
        assertTrue(json.contains("grant-1"),
                "presented-but-unverifiable differs from presented-nothing, for a reviewer");
        assertTrue(json.contains("post-hoc review"), json);
    }

    @Test
    void theRowIsBoundToTheTenant() {
        assertEquals(TENANT, captureSavedRow(TENANT.toString()).getTenantId());
    }

    /**
     * A tenant id that will not parse must not cost us the record. Losing an ungoverned override
     * over a formatting problem is the one outcome worse than the override itself.
     */
    @Test
    void aMalformedTenantStillWritesTheRecord() {
        EventOutboxEntity row = captureSavedRow("not-a-uuid");

        assertEquals(OutboxUngovernedOverrideRecorder.EVENT_TYPE, row.getEventType());
        assertTrue(row.getPayloadJson().contains("clinician-7"));
    }

    /**
     * If the outbox insert fails, the guard must not return an allow. The record is the reason the
     * UNREACHABLE branch is permitted at all, so a silent loss would turn a governed exception into
     * an ungoverned, invisible one.
     */
    @Test
    void aFailedOutboxWriteIsNotSwallowed() {
        doThrow(new IllegalStateException("outbox insert failed"))
                .when(outboxRepository).save(any());

        assertThrows(IllegalStateException.class,
                () -> recorder().record(override(TENANT.toString())));
    }
}
