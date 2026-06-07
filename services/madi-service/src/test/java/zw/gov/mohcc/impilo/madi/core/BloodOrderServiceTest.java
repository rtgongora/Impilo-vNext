package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.domain.BloodOrderStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.OrosIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodOrderEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodOrderServiceTest {

    @Mock private BloodOrderRepository orderRepository;
    @Mock private BloodOrderItemRepository itemRepository;
    @Mock private BloodSampleRepository sampleRepository;
    @Mock private CrossmatchRequestRepository crossmatchRequestRepository;
    @Mock private CrossmatchResultRepository crossmatchResultRepository;
    @Mock private BloodReservationRepository reservationRepository;
    @Mock private BloodIssueRepository issueRepository;
    @Mock private BloodUnitService bloodUnitService;
    @Mock private OrosIntegration orosIntegration;
    @Mock private MadiEventEmitter eventEmitter;

    private BloodOrderService bloodOrderService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bloodOrderService = new BloodOrderService(orderRepository, itemRepository, sampleRepository,
                crossmatchRequestRepository, crossmatchResultRepository, reservationRepository,
                issueRepository, bloodUnitService, orosIntegration, eventEmitter);
    }

    @Test
    void createOrder_setsDraftStatus() {
        BloodOrderEntity order = new BloodOrderEntity();
        order.setTenantId(TENANT_ID);
        order.setPatientCpid("CPID-99");
        order.setBloodGroup("A+");
        order.setComponentType("RED_CELLS");
        when(orderRepository.save(any())).thenAnswer(inv -> {
            BloodOrderEntity o = inv.getArgument(0);
            o.setOrderId(UUID.randomUUID());
            return o;
        });

        BloodOrderEntity saved = bloodOrderService.createOrder(order, Collections.emptyList());

        assertThat(saved.getStatus()).isEqualTo(BloodOrderStatus.DRAFT.name());
        verify(eventEmitter).emit(eq("BLOOD_ORDER"), anyString(), eq("ORDER_CREATED"), anyString(), anyString(), anyMap(), eq(TENANT_ID));
    }

    @Test
    void submit_movesToSubmitted() {
        UUID orderId = UUID.randomUUID();
        BloodOrderEntity order = new BloodOrderEntity();
        order.setOrderId(orderId);
        order.setStatus(BloodOrderStatus.DRAFT.name());
        order.setOrosOrderRef("OROS-1");
        when(orderRepository.findByOrderIdAndTenantId(orderId, TENANT_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BloodOrderEntity result = bloodOrderService.submit(TENANT_ID, orderId);

        assertThat(result.getStatus()).isEqualTo(BloodOrderStatus.SUBMITTED.name());
        verify(orosIntegration).notifyOrderSubmitted("OROS-1", orderId.toString());
    }
}
