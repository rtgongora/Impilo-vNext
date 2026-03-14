package zw.gov.mohcc.impilo.surv.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertDefinitionEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertEventEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.AlertDefinitionRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.AlertEventRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.DailyCounterRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CounterServiceTest {

    @Mock private DailyCounterRepository counterRepository;
    @Mock private AlertDefinitionRepository alertDefRepository;
    @Mock private AlertEventRepository alertEventRepository;

    private CounterService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CounterService(counterRepository, alertDefRepository, alertEventRepository);
    }

    @Test
    @DisplayName("incrementCounter creates new counter when none exists")
    void incrementCounterCreatesNew() {
        when(counterRepository.findByTenantIdAndFacilityIdAndSyndromeCodeAndCountDate(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(counterRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(alertDefRepository.findByTenantIdAndSyndromeCodeAndActiveTrue(any(), any()))
                .thenReturn(List.of());

        DailyCounterEntity result = service.incrementCounter(tenantId, facilityId, "MALARIA", LocalDate.now());

        assertThat(result.getEventCount()).isEqualTo(1);
        assertThat(result.getSyndromeCode()).isEqualTo("MALARIA");
        verify(counterRepository).save(any());
    }

    @Test
    @DisplayName("incrementCounter increments existing counter")
    void incrementCounterIncrementsExisting() {
        DailyCounterEntity existing = new DailyCounterEntity();
        existing.setEventCount(5);
        existing.setTenantId(tenantId);
        existing.setFacilityId(facilityId);
        existing.setSyndromeCode("CHOLERA");
        existing.setCountDate(LocalDate.now());

        when(counterRepository.findByTenantIdAndFacilityIdAndSyndromeCodeAndCountDate(
                any(), any(), any(), any())).thenReturn(Optional.of(existing));
        when(counterRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(alertDefRepository.findByTenantIdAndSyndromeCodeAndActiveTrue(any(), any()))
                .thenReturn(List.of());

        DailyCounterEntity result = service.incrementCounter(tenantId, facilityId, "CHOLERA", LocalDate.now());

        assertThat(result.getEventCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("Alert triggered when counter exceeds threshold")
    void alertTriggeredOnThreshold() {
        DailyCounterEntity existing = new DailyCounterEntity();
        existing.setEventCount(4);
        existing.setTenantId(tenantId);
        existing.setFacilityId(facilityId);
        existing.setSyndromeCode("MEASLES");
        existing.setCountDate(LocalDate.now());

        when(counterRepository.findByTenantIdAndFacilityIdAndSyndromeCodeAndCountDate(
                any(), any(), any(), any())).thenReturn(Optional.of(existing));
        when(counterRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AlertDefinitionEntity alertDef = new AlertDefinitionEntity();
        alertDef.setId(1L);
        alertDef.setThreshold(5);
        alertDef.setSyndromeCode("MEASLES");
        when(alertDefRepository.findByTenantIdAndSyndromeCodeAndActiveTrue(any(), eq("MEASLES")))
                .thenReturn(List.of(alertDef));

        service.incrementCounter(tenantId, facilityId, "MEASLES", LocalDate.now());

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());
        AlertEventEntity alert = captor.getValue();
        assertThat(alert.getTriggerCount()).isEqualTo(5);
        assertThat(alert.getThreshold()).isEqualTo(5);
    }

    @Test
    @DisplayName("No alert when counter below threshold")
    void noAlertBelowThreshold() {
        DailyCounterEntity existing = new DailyCounterEntity();
        existing.setEventCount(2);
        existing.setTenantId(tenantId);
        existing.setFacilityId(facilityId);
        existing.setSyndromeCode("FLU");
        existing.setCountDate(LocalDate.now());

        when(counterRepository.findByTenantIdAndFacilityIdAndSyndromeCodeAndCountDate(
                any(), any(), any(), any())).thenReturn(Optional.of(existing));
        when(counterRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AlertDefinitionEntity alertDef = new AlertDefinitionEntity();
        alertDef.setId(1L);
        alertDef.setThreshold(10);
        when(alertDefRepository.findByTenantIdAndSyndromeCodeAndActiveTrue(any(), eq("FLU")))
                .thenReturn(List.of(alertDef));

        service.incrementCounter(tenantId, facilityId, "FLU", LocalDate.now());

        verify(alertEventRepository, never()).save(any());
    }
}
