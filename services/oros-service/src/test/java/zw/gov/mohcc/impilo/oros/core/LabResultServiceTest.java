package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.api.dto.ObservationInput;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultObservationEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultObservationRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LabResultService} — structured lab-result entry, observation persistence,
 * and per-analyte critical escalation.
 */
@ExtendWith(MockitoExtension.class)
class LabResultServiceTest {

    @Mock private ReportService reportService;
    @Mock private ResultObservationRepository observationRepository;
    @Mock private zw.gov.mohcc.impilo.oros.integration.ButanoIntegration butanoIntegration;

    private LabResultService service;

    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        when(observationRepository.save(any(ResultObservationEntity.class))).thenAnswer(i -> i.getArgument(0));
        service = new LabResultService(reportService, observationRepository,
                OrosTestObjectMapper.create(), butanoIntegration);
    }

    private ResultEntity resultWithId() {
        ResultEntity r = new ResultEntity();
        r.setResultId(UUID.randomUUID());
        r.setOrderId(ORDER_ID);
        return r;
    }

    @Test
    @DisplayName("final result authors a FINAL report, persists observations, flags critical when present")
    void finalWithCritical() {
        ResultEntity result = resultWithId();
        when(reportService.createFinal(eq(ORDER_ID), anyString(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(result);

        List<ObservationInput> obs = List.of(
                new ObservationInput("718-7", "http://loinc.org", "Haemoglobin", null,
                        new BigDecimal("8.1"), "NUMERIC", "g/dL", new BigDecimal("12"),
                        new BigDecimal("16"), "12-16", "L", false, null),
                new ObservationInput("2823-3", "http://loinc.org", "Potassium", null,
                        new BigDecimal("7.1"), "NUMERIC", "mmol/L", new BigDecimal("3.5"),
                        new BigDecimal("5.1"), "3.5-5.1", "HH", true, "repeat urgently"));

        ResultEntity returned = service.recordLabResult(ORDER_ID, true, "anaemia + hyperkalaemia",
                null, "Z1", obs);

        assertThat(returned).isSameAs(result);
        verify(reportService).createFinal(eq(ORDER_ID), anyString(), eq("anaemia + hyperkalaemia"),
                any(), any(), eq("Z1"), org.mockito.ArgumentMatchers.anyList());
        verify(observationRepository, times(2)).save(any(ResultObservationEntity.class));
        // The structured observations are written to the SHR as FHIR Observations.
        verify(butanoIntegration).createObservations(eq(ORDER_ID), eq(result),
                org.mockito.ArgumentMatchers.anyList());
        // The critical potassium triggers the result critical workflow exactly once.
        verify(reportService).flagCritical(eq(result.getResultId()), contains("Potassium"));
    }

    @Test
    @DisplayName("preliminary result with no critical observations does not raise the critical workflow")
    void preliminaryNoCritical() {
        ResultEntity result = resultWithId();
        when(reportService.createPreliminary(eq(ORDER_ID), anyString(), any(), any(), any(), any()))
                .thenReturn(result);

        List<ObservationInput> obs = List.of(
                new ObservationInput(null, null, "Glucose", null, new BigDecimal("5.2"),
                        "NUMERIC", "mmol/L", null, null, "3.9-5.5", "N", false, null));

        service.recordLabResult(ORDER_ID, false, null, null, null, obs);

        verify(reportService).createPreliminary(eq(ORDER_ID), anyString(), any(), any(), any(), any());
        verify(observationRepository, times(1)).save(any(ResultObservationEntity.class));
        verify(reportService, never()).flagCritical(any(), anyString());
    }
}
