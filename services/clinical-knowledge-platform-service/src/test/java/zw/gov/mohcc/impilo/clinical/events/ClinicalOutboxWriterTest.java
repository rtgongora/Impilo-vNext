package zw.gov.mohcc.impilo.clinical.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ClinicalEventOutboxEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RecommendationTraceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.ClinicalEventOutboxRepository;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClinicalOutboxWriterTest {

    @Mock
    ClinicalEventOutboxRepository outboxRepository;

    ClinicalOutboxWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ClinicalOutboxWriter(outboxRepository, new ObjectMapper());
    }

    @Test
    void enqueueRecommendationTrace_persistsOutboxRow() {
        ObjectMapper om = new ObjectMapper();
        RecommendationTraceEntity trace = new RecommendationTraceEntity();
        trace.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        trace.setUserId("u1");
        trace.setRole("DOCTOR");
        trace.setRequestType("ASSISTANT_ASK");
        trace.setSupportMode("SOURCE_GROUNDED");
        trace.setKnowledgeVersion("v1");
        trace.setSourceVersion("2025");
        trace.setInputContextJson(om.createObjectNode().put("tenant_id", "tenant-a"));
        trace.setRecommendationsJson(om.createObjectNode());
        trace.setRulesTriggeredJson(om.createObjectNode().set("alerts", om.createArrayNode()));

        writer.enqueueRecommendationTrace(trace);

        ArgumentCaptor<ClinicalEventOutboxEntity> cap = ArgumentCaptor.forClass(ClinicalEventOutboxEntity.class);
        verify(outboxRepository).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo("GUIDANCE_RECOMMENDATION_GENERATED");
        assertThat(cap.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(cap.getValue().getPayload().path("trace_id").asText()).contains("11111111");
    }

    @Test
    void enqueue_writesGenericPayload() {
        writer.enqueue("GUIDANCE_OVERRIDE_RECORDED", "t1", "OverrideRecord", "ov-1", Map.of("x", 1));
        ArgumentCaptor<ClinicalEventOutboxEntity> cap = ArgumentCaptor.forClass(ClinicalEventOutboxEntity.class);
        verify(outboxRepository).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo("GUIDANCE_OVERRIDE_RECORDED");
        assertThat(cap.getValue().getPayload().path("x").asInt()).isEqualTo(1);
    }
}
