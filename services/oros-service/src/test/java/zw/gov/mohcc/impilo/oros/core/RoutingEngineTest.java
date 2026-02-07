package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.config.OrosProperties;
import zw.gov.mohcc.impilo.oros.domain.*;
import zw.gov.mohcc.impilo.oros.persistence.entity.*;
import zw.gov.mohcc.impilo.oros.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RoutingEngine}.
 *
 * <p>Validates routing mode resolution, target selection,
 * hybrid mode reconcile entry creation, and retry logic.</p>
 */
@ExtendWith(MockitoExtension.class)
class RoutingEngineTest {

    @Mock
    private RoutingRepository routingRepository;

    @Mock
    private CapabilityRepository capabilityRepository;

    @Mock
    private ReconcileQueueRepository reconcileQueueRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private RoutingEngine routingEngine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "clinician-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OrosProperties properties = new OrosProperties();
        properties.getRouting().setDefaultMode("INTERNAL");
        ObjectMapper objectMapper = new ObjectMapper();

        routingEngine = new RoutingEngine(
                routingRepository, capabilityRepository, reconcileQueueRepository,
                outboxRepository, properties, objectMapper);
    }

    private OrderEntity createLabOrder() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        order.setTenantId(TENANT_ID);
        order.setFacilityId(FACILITY_ID);
        order.setPatientCpid("CPID-001");
        order.setOrderType(OrderType.LAB);
        order.setPriority(OrderPriority.ROUTINE);
        order.setStatus(OrderStatus.PLACED);
        return order;
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    @Test
    @DisplayName("INTERNAL mode routes to worklist with SENT status")
    void internalModeRoutesToWorklist() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            CapabilityEntity capability = new CapabilityEntity();
            capability.setAdapterMode(AdapterMode.INTERNAL);
            when(capabilityRepository.findByTenantIdAndFacilityIdAndOrderType(
                    TENANT_ID, FACILITY_ID, OrderType.LAB))
                    .thenReturn(Optional.of(capability));
            when(routingRepository.save(any(RoutingEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            OrderEntity order = createLabOrder();
            RoutingEntity route = routingEngine.routeOrder(order);

            assertThat(route.getRouteTarget()).isEqualTo(RouteTarget.INTERNAL);
            assertThat(route.getAdapterMode()).isEqualTo(AdapterMode.INTERNAL);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.SENT);

            // No reconcile entry for INTERNAL mode
            verify(reconcileQueueRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("ADAPTER mode dispatches to LIMS for LAB order")
    void adapterModeDispatchesViaAdapter() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            CapabilityEntity capability = new CapabilityEntity();
            capability.setAdapterMode(AdapterMode.ADAPTER);
            when(capabilityRepository.findByTenantIdAndFacilityIdAndOrderType(
                    TENANT_ID, FACILITY_ID, OrderType.LAB))
                    .thenReturn(Optional.of(capability));
            when(routingRepository.save(any(RoutingEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            OrderEntity order = createLabOrder();
            RoutingEntity route = routingEngine.routeOrder(order);

            assertThat(route.getRouteTarget()).isEqualTo(RouteTarget.LIMS);
            assertThat(route.getAdapterMode()).isEqualTo(AdapterMode.ADAPTER);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.PENDING);

            // No reconcile entry for ADAPTER mode
            verify(reconcileQueueRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("HYBRID mode creates both route and reconcile entry")
    void hybridModeCreatesBothAndReconcileEntry() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            CapabilityEntity capability = new CapabilityEntity();
            capability.setAdapterMode(AdapterMode.HYBRID);
            when(capabilityRepository.findByTenantIdAndFacilityIdAndOrderType(
                    TENANT_ID, FACILITY_ID, OrderType.LAB))
                    .thenReturn(Optional.of(capability));
            when(routingRepository.save(any(RoutingEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(reconcileQueueRepository.save(any(ReconcileQueueEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            OrderEntity order = createLabOrder();
            RoutingEntity route = routingEngine.routeOrder(order);

            assertThat(route.getRouteTarget()).isEqualTo(RouteTarget.LIMS);
            assertThat(route.getAdapterMode()).isEqualTo(AdapterMode.HYBRID);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.PENDING);

            // Verify reconcile entry was created
            ArgumentCaptor<ReconcileQueueEntity> captor = ArgumentCaptor.forClass(ReconcileQueueEntity.class);
            verify(reconcileQueueRepository).save(captor.capture());

            ReconcileQueueEntity reconcileEntry = captor.getValue();
            assertThat(reconcileEntry.getOrderId()).isEqualTo(order.getOrderId());
            assertThat(reconcileEntry.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(reconcileEntry.getPatientCpid()).isEqualTo("CPID-001");
            assertThat(reconcileEntry.getStatus()).isEqualTo(ReconcileStatus.PENDING);
        }
    }

    @Test
    @DisplayName("Fallback to default INTERNAL mode when no facility-specific capability exists")
    void fallbackToDefaultWhenNoCapability() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            // No facility-specific and no tenant-level capability
            when(capabilityRepository.findByTenantIdAndFacilityIdAndOrderType(
                    TENANT_ID, FACILITY_ID, OrderType.LAB))
                    .thenReturn(Optional.empty());
            when(capabilityRepository.findByTenantIdAndFacilityIdIsNullAndOrderType(
                    TENANT_ID, OrderType.LAB))
                    .thenReturn(Optional.empty());
            when(routingRepository.save(any(RoutingEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            OrderEntity order = createLabOrder();
            RoutingEntity route = routingEngine.routeOrder(order);

            // Should fall back to default INTERNAL mode
            assertThat(route.getRouteTarget()).isEqualTo(RouteTarget.INTERNAL);
            assertThat(route.getAdapterMode()).isEqualTo(AdapterMode.INTERNAL);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.SENT);
        }
    }

    @Test
    @DisplayName("ADAPTER mode routes IMAGING order to PACS")
    void adapterModeRoutesImagingToPacs() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            CapabilityEntity capability = new CapabilityEntity();
            capability.setAdapterMode(AdapterMode.ADAPTER);
            when(capabilityRepository.findByTenantIdAndFacilityIdAndOrderType(
                    TENANT_ID, FACILITY_ID, OrderType.IMAGING))
                    .thenReturn(Optional.of(capability));
            when(routingRepository.save(any(RoutingEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            OrderEntity order = createLabOrder();
            order.setOrderType(OrderType.IMAGING);
            RoutingEntity route = routingEngine.routeOrder(order);

            assertThat(route.getRouteTarget()).isEqualTo(RouteTarget.PACS);
        }
    }

    @Test
    @DisplayName("retryRoute fails for non-FAILED route")
    void retryRouteRejectsNonFailed() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            RoutingEntity route = new RoutingEntity();
            route.setStatus(RouteStatus.SENT);
            when(routingRepository.findByOrderId("ORD-001")).thenReturn(List.of(route));

            assertThatThrownBy(() -> routingEngine.retryRoute("ORD-001"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot retry route in status SENT");
        }
    }
}
