package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.domain.BloodUnitStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodCollectionEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodUnitEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodCollectionRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodUnitRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodUnitServiceTest {

    @Mock private BloodUnitRepository bloodUnitRepository;
    @Mock private BloodCollectionRepository collectionRepository;
    @Mock private MadiEventEmitter eventEmitter;

    private BloodUnitService bloodUnitService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bloodUnitService = new BloodUnitService(bloodUnitRepository, collectionRepository, eventEmitter);
    }

    @Test
    void createFromCollection_createsUnit() {
        UUID collectionId = UUID.randomUUID();
        BloodCollectionEntity collection = new BloodCollectionEntity();
        collection.setCollectionId(collectionId);
        collection.setBagNumber("BAG-001");
        when(collectionRepository.findByCollectionIdAndTenantId(collectionId, TENANT_ID))
                .thenReturn(Optional.of(collection));
        when(bloodUnitRepository.save(any())).thenAnswer(inv -> {
            BloodUnitEntity u = inv.getArgument(0);
            u.setUnitId(UUID.randomUUID());
            return u;
        });

        BloodUnitEntity unit = bloodUnitService.createFromCollection(
                TENANT_ID, collectionId, "O+", "POS", UUID.randomUUID(), UUID.randomUUID());

        assertThat(unit.getBagNumber()).isEqualTo("BAG-001");
        assertThat(unit.getStatus()).isEqualTo(BloodUnitStatus.COLLECTED.name());
    }

    @Test
    void transition_rejectsInvalidTransition() {
        UUID unitId = UUID.randomUUID();
        BloodUnitEntity unit = new BloodUnitEntity();
        unit.setUnitId(unitId);
        unit.setStatus(BloodUnitStatus.COLLECTED.name());
        when(bloodUnitRepository.findByUnitIdAndTenantId(unitId, TENANT_ID)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> bloodUnitService.transition(TENANT_ID, unitId, BloodUnitStatus.ISSUED))
                .isInstanceOf(IllegalStateException.class);
    }
}
