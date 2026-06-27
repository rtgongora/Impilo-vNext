package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.integration.ShareSlipClient;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalResultLinkServiceTest {

    @Mock private ResultRepository resultRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ShareSlipClient shareSlipClient;
    @Mock private EventOutboxRepository outboxRepository;

    private ExternalResultLinkService service;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper om = OrosTestObjectMapper.create();
        service = new ExternalResultLinkService(resultRepository, orderRepository, shareSlipClient, outboxRepository, om);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "dr-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    @Test
    @DisplayName("issue delegates to share-slip with the result's documents and emits an event")
    void issuesLink() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            UUID resultId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            ResultEntity r = new ResultEntity();
            r.setResultId(resultId);
            r.setOrderId("ORD-1");
            r.setDocIds("[\"" + docId + "\"]");
            when(resultRepository.findById(resultId)).thenReturn(Optional.of(r));
            OrderEntity order = new OrderEntity();
            order.setOrderId("ORD-1");
            order.setAccessionNumber("ACC-2026-AB-1");
            when(orderRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(order));
            when(shareSlipClient.createLink(eq("DIAGNOSTIC_RESULT"), eq(resultId.toString()), any(),
                    eq(List.of(docId)), any(), eq(72), eq(3)))
                    .thenReturn(new ShareSlipClient.ShareLinkRef("link-1", "tok-1", "ACTIVE", "2026-07-01T00:00:00Z"));

            ShareSlipClient.ShareLinkRef ref = service.issue(resultId, 72, 3);

            assertThat(ref.linkId()).isEqualTo("link-1");
            verify(outboxRepository).save(any(EventOutboxEntity.class));
        }
    }

    @Test
    @DisplayName("a result with no documents cannot be shared externally")
    void rejectsNoDocuments() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            UUID resultId = UUID.randomUUID();
            ResultEntity r = new ResultEntity();
            r.setResultId(resultId);
            r.setOrderId("ORD-1");
            r.setDocIds(null);
            when(resultRepository.findById(resultId)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.issue(resultId, 72, 3))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no documents");
            verifyNoInteractions(shareSlipClient);
        }
    }

    @Test
    @DisplayName("missing result is rejected")
    void rejectsMissingResult() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            UUID resultId = UUID.randomUUID();
            when(resultRepository.findById(resultId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.issue(resultId, 72, 3))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
