package zw.gov.mohcc.impilo.reporting.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.reporting.persistence.entity.RptTheatreCaseMetricEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.RptTheatreCaseMetricRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Wave 4 §20 — theatre.* event → report metric projection. */
@ExtendWith(MockitoExtension.class)
class TheatreReportingConsumerTest {

    @Mock RptTheatreCaseMetricRepository repo;
    private TheatreReportingConsumer consumer;

    private final UUID tenant = UUID.randomUUID();
    private final UUID episode = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new TheatreReportingConsumer(repo, new ObjectMapper());
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private String event(String type, String extra) {
        return "{\"event_type\":\"" + type + "\",\"tenant_id\":\"" + tenant + "\",\"episode_id\":\"" + episode + "\""
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    @Test
    void projectsCompletedCaseIntoMetric() {
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.empty());
        consumer.consumeTheatreEvent(event("theatre.case.completed",
                "\"procedure_code\":\"LAP-CHOLE\",\"theatre_minutes\":90,\"has_anaesthesia\":true,"
                + "\"implant_count\":2,\"consumable_count\":5,\"blood_units\":1"));

        ArgumentCaptor<RptTheatreCaseMetricEntity> cap = ArgumentCaptor.forClass(RptTheatreCaseMetricEntity.class);
        verify(repo).save(cap.capture());
        RptTheatreCaseMetricEntity m = cap.getValue();
        assertEquals("COMPLETED", m.getStatus());
        assertEquals(90, m.getTheatreMinutes());
        assertTrue(m.isHasAnaesthesia());
        assertEquals(2, m.getImplantCount());
        assertEquals(1, m.getBloodUnits());
        assertEquals("LAP-CHOLE", m.getProcedureCode());
    }

    @Test
    void projectsCancellationWithReason() {
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.empty());
        consumer.consumeTheatreEvent(event("theatre.case.cancelled", "\"reason\":\"Patient not fasted\""));
        ArgumentCaptor<RptTheatreCaseMetricEntity> cap = ArgumentCaptor.forClass(RptTheatreCaseMetricEntity.class);
        verify(repo).save(cap.capture());
        assertTrue(cap.getValue().isCancelled());
        assertEquals("Patient not fasted", cap.getValue().getCancellationReason());
    }

    @Test
    void safetyEventFlagsComplicationAndIncrements() {
        RptTheatreCaseMetricEntity existing = new RptTheatreCaseMetricEntity();
        existing.setId(1L);
        existing.setTenantId(tenant);
        existing.setEpisodeId(episode);
        existing.setSafetyEventCount(1);
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.of(existing));
        consumer.consumeTheatreEvent(event("theatre.safety.retained_item", "\"severity\":\"CRITICAL\""));
        assertTrue(existing.isComplicationFlag());
        assertEquals(2, existing.getSafetyEventCount());
    }

    @Test
    void ignoresNonTheatreAndUntrackedEvents() {
        consumer.consumeTheatreEvent(event("theatre.anaesthesia.charted", ""));
        consumer.consumeTheatreEvent("{\"event_type\":\"inpatient.admission.created\"}");
        verify(repo, never()).save(any());
    }

    /**
     * Wave 7 §17 — REPLAY SAFETY. At-least-once Kafka delivery means a completed-case event can be
     * re-delivered (rebalance / offset replay). The projection must upsert the SAME per-case row via the
     * (tenant_id, episode_id) key — never mint a second metric — and the terminal values must be stable.
     * This is the unit twin of the live drainage-rig assertion that a replay produces no duplicate row.
     */
    @Test
    void replayedCompletedCaseUpsertsSameRowNoDuplicateMetric() {
        String completed = event("theatre.case.completed",
                "\"procedure_code\":\"LAP-CHOLE\",\"theatre_minutes\":90,\"has_anaesthesia\":true,"
                + "\"implant_count\":2,\"consumable_count\":5,\"blood_units\":1");

        // First delivery: no existing row → a new metric is created and saved.
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.empty());
        consumer.consumeTheatreEvent(completed);
        ArgumentCaptor<RptTheatreCaseMetricEntity> firstCap = ArgumentCaptor.forClass(RptTheatreCaseMetricEntity.class);
        verify(repo).save(firstCap.capture());
        RptTheatreCaseMetricEntity row = firstCap.getValue();
        assertEquals("COMPLETED", row.getStatus());
        assertEquals(90, row.getTheatreMinutes());

        // Second (replayed) delivery: the SAME keyed row is now found and re-saved in place — no new row.
        row.setId(42L); // simulate persistence-assigned id on the existing projection
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.of(row));
        consumer.consumeTheatreEvent(completed);

        ArgumentCaptor<RptTheatreCaseMetricEntity> replayCap = ArgumentCaptor.forClass(RptTheatreCaseMetricEntity.class);
        verify(repo, times(2)).save(replayCap.capture());
        RptTheatreCaseMetricEntity replayed = replayCap.getValue();
        assertSame(row, replayed, "replay must re-save the SAME keyed metric, not a fresh entity");
        assertEquals(42L, replayed.getId(), "replay must not mint a second row");
        assertEquals("COMPLETED", replayed.getStatus());
        assertEquals(90, replayed.getTheatreMinutes());
        assertEquals(2, replayed.getImplantCount());
        assertEquals(1, replayed.getBloodUnits());
    }
}
