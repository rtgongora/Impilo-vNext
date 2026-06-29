package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockSource;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockStatus;
import zw.gov.mohcc.impilo.inventory.domain.RefillStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ClientHomeStockEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RefillRequestEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ClientHomeStockRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.RefillRequestRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HouseholdStockServiceTest {

    @Mock private ClientHomeStockRepository homeStockRepository;
    @Mock private RefillRequestRepository refillRepository;
    @InjectMocks private HouseholdStockServiceImpl service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String CLIENT = "CPID-123";

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                TENANT, CLIENT, "CITIZEN", "SELF_CARE", null,
                UUID.randomUUID(), null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    @DisplayName("addHomeStock with zero qty is DEPLETED")
    void addHomeStock_zeroDepleted() {
        when(homeStockRepository.save(any(ClientHomeStockEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ClientHomeStockEntity item = service.addHomeStock(CLIENT, "INS", "Insulin", 0,
                "vial", null, null, HomeStockSource.DISPENSED, null, null);
        assertThat(item.getStatus()).isEqualTo(HomeStockStatus.DEPLETED);
        assertThat(item.getClientId()).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("addHomeStock with positive qty is ACTIVE")
    void addHomeStock_active() {
        when(homeStockRepository.save(any(ClientHomeStockEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ClientHomeStockEntity item = service.addHomeStock(CLIENT, "INS", "Insulin", 3,
                "vial", null, null, HomeStockSource.DISPENSED, null, null);
        assertThat(item.getStatus()).isEqualTo(HomeStockStatus.ACTIVE);
        assertThat(item.getQty()).isEqualTo(3);
    }

    @Test
    @DisplayName("updateQuantity to zero marks DEPLETED")
    void updateQuantity_toZero() {
        UUID id = UUID.randomUUID();
        ClientHomeStockEntity item = new ClientHomeStockEntity();
        item.setHomeStockId(id);
        item.setTenantId(TENANT);
        item.setStatus(HomeStockStatus.ACTIVE);
        item.setQty(5);
        when(homeStockRepository.findById(id)).thenReturn(Optional.of(item));
        when(homeStockRepository.save(any(ClientHomeStockEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientHomeStockEntity result = service.updateQuantity(id, 0);
        assertThat(result.getQty()).isZero();
        assertThat(result.getStatus()).isEqualTo(HomeStockStatus.DEPLETED);
    }

    @Test
    @DisplayName("requestRefill creates a REQUESTED refill bound to the actor")
    void requestRefill_creates() {
        when(refillRepository.save(any(RefillRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        RefillRequestEntity refill = service.requestRefill(CLIENT, "INS", "Insulin", 2, null, "running low");
        assertThat(refill.getStatus()).isEqualTo(RefillStatus.REQUESTED);
        assertThat(refill.getRequestedBy()).isEqualTo(CLIENT);
        assertThat(refill.getQtyRequested()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateRefillStatus to FULFILLED stamps fulfilledAt")
    void updateRefillStatus_fulfilled() {
        UUID id = UUID.randomUUID();
        RefillRequestEntity refill = new RefillRequestEntity();
        refill.setRefillId(id);
        refill.setTenantId(TENANT);
        refill.setStatus(RefillStatus.APPROVED);
        when(refillRepository.findById(id)).thenReturn(Optional.of(refill));
        when(refillRepository.save(any(RefillRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RefillRequestEntity result = service.updateRefillStatus(id, RefillStatus.FULFILLED, null);
        assertThat(result.getStatus()).isEqualTo(RefillStatus.FULFILLED);
        assertThat(result.getFulfilledAt()).isNotNull();
    }

    @Test
    @DisplayName("updateRefillStatus rejects a terminal refill")
    void updateRefillStatus_terminal() {
        UUID id = UUID.randomUUID();
        RefillRequestEntity refill = new RefillRequestEntity();
        refill.setRefillId(id);
        refill.setTenantId(TENANT);
        refill.setStatus(RefillStatus.FULFILLED);
        when(refillRepository.findById(id)).thenReturn(Optional.of(refill));

        assertThatThrownBy(() -> service.updateRefillStatus(id, RefillStatus.CANCELLED, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already");
    }
}
