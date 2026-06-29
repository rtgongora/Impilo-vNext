package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.domain.ColdChainLocationType;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionBreach;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionResolution;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionStatus;
import zw.gov.mohcc.impilo.inventory.domain.TemperatureSource;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainExcursionEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainLocationEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.TemperatureLogEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ColdChainExcursionRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ColdChainLocationRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.TemperatureLogRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ColdChainServiceTest {

    @Mock private ColdChainLocationRepository locationRepository;
    @Mock private TemperatureLogRepository logRepository;
    @Mock private ColdChainExcursionRepository excursionRepository;
    @InjectMocks private ColdChainServiceImpl service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                TENANT, "nurse-2", "STAFF", "LOGISTICS", null,
                UUID.randomUUID(), null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private ColdChainLocationEntity fridge() {
        ColdChainLocationEntity l = new ColdChainLocationEntity();
        l.setCcLocationId(LOCATION);
        l.setTenantId(TENANT);
        l.setName("EPI Fridge 1");
        l.setType(ColdChainLocationType.FRIDGE);
        l.setTempMin(2.0);
        l.setTempMax(8.0);
        return l;
    }

    @Test
    @DisplayName("registerLocation rejects inverted range")
    void registerLocation_invertedRange() {
        assertThatThrownBy(() -> service.registerLocation(UUID.randomUUID(), null, "F",
                ColdChainLocationType.FRIDGE, 8.0, 2.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tempMin must not exceed tempMax");
    }

    @Test
    @DisplayName("logTemperature within range records no excursion")
    void logTemperature_withinRange() {
        when(locationRepository.findById(LOCATION)).thenReturn(Optional.of(fridge()));
        when(logRepository.save(any(TemperatureLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ColdChainService.LogResult result = service.logTemperature(LOCATION, 5.0, TemperatureSource.MANUAL, null);

        assertThat(result.log().isWithinRange()).isTrue();
        assertThat(result.excursion()).isNull();
        verify(excursionRepository, never()).save(any());
    }

    @Test
    @DisplayName("logTemperature above range raises a HIGH excursion")
    void logTemperature_highExcursion() {
        when(locationRepository.findById(LOCATION)).thenReturn(Optional.of(fridge()));
        when(logRepository.save(any(TemperatureLogEntity.class))).thenAnswer(inv -> {
            TemperatureLogEntity l = inv.getArgument(0);
            l.setLogId(UUID.randomUUID());
            return l;
        });
        when(excursionRepository.save(any(ColdChainExcursionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ColdChainService.LogResult result = service.logTemperature(LOCATION, 12.5, TemperatureSource.IOT, null);

        assertThat(result.log().isWithinRange()).isFalse();
        assertThat(result.excursion()).isNotNull();
        assertThat(result.excursion().getBreach()).isEqualTo(ExcursionBreach.HIGH);
        assertThat(result.excursion().getStatus()).isEqualTo(ExcursionStatus.OPEN);
        assertThat(result.excursion().getLogId()).isEqualTo(result.log().getLogId());
    }

    @Test
    @DisplayName("logTemperature below range raises a LOW excursion")
    void logTemperature_lowExcursion() {
        when(locationRepository.findById(LOCATION)).thenReturn(Optional.of(fridge()));
        when(logRepository.save(any(TemperatureLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(excursionRepository.save(any(ColdChainExcursionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ColdChainService.LogResult result = service.logTemperature(LOCATION, -1.0, TemperatureSource.MANUAL, null);

        assertThat(result.excursion().getBreach()).isEqualTo(ExcursionBreach.LOW);
    }

    @Test
    @DisplayName("resolveExcursion sets RESOLVED with resolution")
    void resolveExcursion_success() {
        UUID id = UUID.randomUUID();
        ColdChainExcursionEntity ex = new ColdChainExcursionEntity();
        ex.setExcursionId(id);
        ex.setTenantId(TENANT);
        ex.setStatus(ExcursionStatus.OPEN);
        when(excursionRepository.findById(id)).thenReturn(Optional.of(ex));
        when(excursionRepository.save(any(ColdChainExcursionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ColdChainExcursionEntity result = service.resolveExcursion(id, ExcursionResolution.WASTAGE, "discarded");

        assertThat(result.getStatus()).isEqualTo(ExcursionStatus.RESOLVED);
        assertThat(result.getResolution()).isEqualTo(ExcursionResolution.WASTAGE);
        assertThat(result.getResolvedAt()).isNotNull();
        assertThat(result.getResolvedBy()).isEqualTo("nurse-2");
    }

    @Test
    @DisplayName("resolveExcursion rejects an already-resolved excursion")
    void resolveExcursion_alreadyResolved() {
        UUID id = UUID.randomUUID();
        ColdChainExcursionEntity ex = new ColdChainExcursionEntity();
        ex.setExcursionId(id);
        ex.setTenantId(TENANT);
        ex.setStatus(ExcursionStatus.RESOLVED);
        when(excursionRepository.findById(id)).thenReturn(Optional.of(ex));

        assertThatThrownBy(() -> service.resolveExcursion(id, ExcursionResolution.RELEASED, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already resolved");
    }
}
