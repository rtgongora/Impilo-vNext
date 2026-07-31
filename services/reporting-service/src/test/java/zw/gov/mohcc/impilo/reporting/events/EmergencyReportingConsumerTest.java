package zw.gov.mohcc.impilo.reporting.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.reporting.persistence.entity.RptEmergencyEpisodeMetricEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.RptEmergencyEpisodeMetricRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** W17 — pct.emergency.* → rpt_emergency_episode_metric projection. */
@ExtendWith(MockitoExtension.class)
class EmergencyReportingConsumerTest {

    @Mock RptEmergencyEpisodeMetricRepository repo;
    private EmergencyReportingConsumer consumer;

    private final UUID tenant = UUID.randomUUID();
    private final UUID episode = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new EmergencyReportingConsumer(repo, new ObjectMapper());
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private String episodeEvent(String type, String extra) {
        return "{\"event_type\":\"" + type + "\",\"tenantId\":\"" + tenant + "\",\"episodeId\":\"" + episode + "\""
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    @Test
    void projectsOpenedEpisode() {
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.empty());
        consumer.consumeEmergencyEvent(episodeEvent("EMERGENCY_EPISODE_OPENED",
                "\"state\":\"OPEN_UNTRIAGED\",\"entryRoute\":\"WALK_IN\",\"episodeReference\":\"EM-26-ABC12\""));

        ArgumentCaptor<RptEmergencyEpisodeMetricEntity> cap = ArgumentCaptor.forClass(RptEmergencyEpisodeMetricEntity.class);
        verify(repo).save(cap.capture());
        RptEmergencyEpisodeMetricEntity m = cap.getValue();
        assertEquals("OPEN_UNTRIAGED", m.getState());
        assertEquals("WALK_IN", m.getEntryRoute());
        assertEquals("EM-26-ABC12", m.getEpisodeReference());
        assertNotNull(m.getOpenedAt());
        assertFalse(m.isClosed());
    }

    @Test
    void projectsDeathClosureFlags() {
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.empty());
        consumer.consumeEmergencyEvent(episodeEvent("EMERGENCY_EPISODE_STATE_CHANGED",
                "\"state\":\"CLOSED_DIED\",\"previousState\":\"OPEN_IN_CARE\",\"outcome\":\"DIED\""));

        ArgumentCaptor<RptEmergencyEpisodeMetricEntity> cap = ArgumentCaptor.forClass(RptEmergencyEpisodeMetricEntity.class);
        verify(repo).save(cap.capture());
        RptEmergencyEpisodeMetricEntity m = cap.getValue();
        assertTrue(m.isClosed());
        assertTrue(m.isDied());
        assertEquals("DIED", m.getDispositionType());
    }

    @Test
    void projectsLwbs() {
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.empty());
        consumer.consumeEmergencyEvent(episodeEvent("EMERGENCY_EPISODE_STATE_CHANGED",
                "\"state\":\"CLOSED_LEFT_BEFORE_ASSESSMENT\""));
        ArgumentCaptor<RptEmergencyEpisodeMetricEntity> cap = ArgumentCaptor.forClass(RptEmergencyEpisodeMetricEntity.class);
        verify(repo).save(cap.capture());
        assertTrue(cap.getValue().isLeftWithoutBeingSeen());
        assertTrue(cap.getValue().isClosed());
    }

    @Test
    void projectsAlertOverdueAndRitoLink() {
        RptEmergencyEpisodeMetricEntity existing = new RptEmergencyEpisodeMetricEntity();
        existing.setId(1L);
        existing.setTenantId(tenant);
        existing.setEpisodeId(episode);
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.of(existing));

        consumer.consumeEmergencyEvent(episodeEvent("EMERGENCY_ALERT_OVERDUE",
                "\"ritoCaseRef\":\"rito-case-1\""));
        assertEquals(1, existing.getAlertOverdueCount());
        assertTrue(existing.isRitoCaseLinked());
    }

    @Test
    void projectsHandoverExpiry() {
        RptEmergencyEpisodeMetricEntity existing = new RptEmergencyEpisodeMetricEntity();
        existing.setId(2L);
        existing.setTenantId(tenant);
        existing.setEpisodeId(episode);
        existing.setHandoverCount(1);
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.of(existing));

        consumer.consumeEmergencyEvent(episodeEvent("EMERGENCY_HANDOVER_EXPIRED", ""));
        assertEquals(1, existing.getHandoverExpiredCount());
    }

    @Test
    void skipsWhenEventTypeMissing() {
        consumer.consumeEmergencyEvent("{\"tenantId\":\"" + tenant + "\",\"episodeId\":\"" + episode + "\"}");
        verify(repo, never()).save(any());
    }

    @Test
    void neverInventAcuityWhenAbsent() {
        when(repo.findByTenantIdAndEpisodeId(tenant, episode)).thenReturn(Optional.empty());
        consumer.consumeEmergencyEvent(episodeEvent("EMERGENCY_EPISODE_OPENED", "\"state\":\"OPEN_UNTRIAGED\""));
        ArgumentCaptor<RptEmergencyEpisodeMetricEntity> cap = ArgumentCaptor.forClass(RptEmergencyEpisodeMetricEntity.class);
        verify(repo).save(cap.capture());
        assertNull(cap.getValue().getTriageAcuity());
    }
}
