package zw.gov.mohcc.impilo.clinical.curation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.clinical.events.ClinicalOutboxWriter;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ClinicalKnowledgeItemEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.KnowledgeReviewItemEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.ClinicalKnowledgeItemRepository;
import zw.gov.mohcc.impilo.clinical.persistence.repository.KnowledgeReviewItemRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeCurationServiceTest {

    @Mock
    KnowledgeReviewItemRepository reviewItemRepository;

    @Mock
    ClinicalKnowledgeItemRepository knowledgeItemRepository;

    @Mock
    ClinicalOutboxWriter outboxWriter;

    ObjectMapper objectMapper = new ObjectMapper();

    KnowledgeCurationService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeCurationService(reviewItemRepository, knowledgeItemRepository, outboxWriter, objectMapper);
    }

    @Test
    void approve_createsKnowledgeItemAndOutbox() {
        UUID rid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        KnowledgeReviewItemEntity row = new KnowledgeReviewItemEntity();
        row.setId(rid);
        row.setReviewStatus("PROPOSED");
        row.setProposedPayload(objectMapper.createObjectNode()
                .put("type", "SOURCE_EXCERPT")
                .put("document_id", "a0000001-0001-4001-8001-000000000001")
                .put("section_path", "pdf/p1c0")
                .put("title", "Test")
                .put("excerpt", "body text"));
        when(reviewItemRepository.findById(rid)).thenReturn(Optional.of(row));
        when(knowledgeItemRepository.save(any(ClinicalKnowledgeItemEntity.class))).thenAnswer(inv -> {
            ClinicalKnowledgeItemEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
            }
            return e;
        });

        Map<String, Object> result = service.decide(rid, "APPROVED", "curator-1", "ok");

        assertThat(result.get("status")).isEqualTo("APPROVED");
        verify(knowledgeItemRepository).save(any(ClinicalKnowledgeItemEntity.class));
        verify(outboxWriter).enqueue(
                org.mockito.ArgumentMatchers.eq("KNOWLEDGE_ITEM_APPROVED"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
        ArgumentCaptor<KnowledgeReviewItemEntity> cap = ArgumentCaptor.forClass(KnowledgeReviewItemEntity.class);
        verify(reviewItemRepository).save(cap.capture());
        assertThat(cap.getValue().getReviewStatus()).isEqualTo("APPROVED");
    }
}
