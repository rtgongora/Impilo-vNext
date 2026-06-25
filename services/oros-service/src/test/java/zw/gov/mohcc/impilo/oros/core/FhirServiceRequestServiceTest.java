package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FhirServiceRequestServiceTest {

    @Mock private OrderStateMachine stateMachine;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderSubmissionService submissionService;
    @Mock private RoutingEngine routingEngine;
    @Mock private WorkstepEngine workstepEngine;
    @Mock private SlaService slaService;

    private final ObjectMapper om = new ObjectMapper();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID FACILITY = UUID.randomUUID();

    private FhirServiceRequestService service() {
        return new FhirServiceRequestService(stateMachine, orderRepository, submissionService,
                routingEngine, workstepEngine, slaService);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT, "gw-1", "SYSTEM", "TREATMENT",
                null, UUID.randomUUID(), FACILITY, UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private JsonNode serviceRequest() throws Exception {
        return om.readTree("""
            {"resourceType":"ServiceRequest","priority":"stat",
             "identifier":[{"system":"urn:ext:lis","value":"EXT-123"}],
             "subject":{"reference":"Patient/CPID-1"},
             "category":[{"coding":[{"code":"imaging"}]}],
             "code":{"coding":[{"code":"CHEST-XR","display":"Chest X-Ray"}]},
             "note":[{"text":"Cough 2 weeks"}]}
            """);
    }

    private OrderEntity order(String id, OrderStatus status) {
        OrderEntity o = new OrderEntity();
        o.setOrderId(id);
        o.setStatus(status);
        o.setRequestSource(RequestSource.EXTERNAL);
        return o;
    }

    @Test
    @DisplayName("ingest maps a ServiceRequest to an EXTERNAL imaging order and submits it")
    void ingestMapsAndSubmits() throws Exception {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(orderRepository.findByTenantIdAndExternalOrderRef(eq(TENANT), any())).thenReturn(Optional.empty());
            OrderEntity draft = order("ORD-NEW", OrderStatus.DRAFT);
            when(stateMachine.createDraft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(draft);
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(submissionService.submit("ORD-NEW")).thenReturn(order("ORD-NEW", OrderStatus.PLACED));

            OrderEntity result = service().ingest(serviceRequest());

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PLACED);
            assertThat(draft.getExternalOrderRef()).isEqualTo("urn:ext:lis|EXT-123");
            verify(stateMachine).createDraft(eq(FACILITY), eq("CPID-1"), eq(OrderType.IMAGING),
                    eq(OrderPriority.STAT), eq(RequestSource.EXTERNAL),
                    any(), any(), eq("Cough 2 weeks"), any(), any(), any(), any(), any());
            verify(routingEngine).routeOrder(any());
            verify(slaService).startTimer(eq("ORD-NEW"), any(), anyInt());
        }
    }

    @Test
    @DisplayName("ingest is idempotent on the external identifier")
    void ingestIdempotent() throws Exception {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            OrderEntity existing = order("ORD-EXISTING", OrderStatus.PLACED);
            when(orderRepository.findByTenantIdAndExternalOrderRef(TENANT, "urn:ext:lis|EXT-123"))
                    .thenReturn(Optional.of(existing));

            OrderEntity result = service().ingest(serviceRequest());

            assertThat(result.getOrderId()).isEqualTo("ORD-EXISTING");
            verify(stateMachine, never()).createDraft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }
}
