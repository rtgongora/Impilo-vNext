package zw.gov.mohcc.impilo.oros.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.oros.domain.ResultStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultObservationEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the generalised FHIR Observation writeback ({@link ButanoIntegration#createObservations})
 * — value[x] + UCUM unit + referenceRange + interpretation per analyte.
 */
@ExtendWith(MockitoExtension.class)
class ButanoObservationsTest {

    @Mock private RestTemplate restTemplate;

    private ResultEntity finalResult() {
        ResultEntity r = new ResultEntity();
        r.setResultId(UUID.randomUUID());
        r.setOrderId("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        r.setReportStatus(ResultStatus.FINAL);
        return r;
    }

    private ResultObservationEntity obs(String name, BigDecimal value, String unit, String flag, boolean critical) {
        ResultObservationEntity o = new ResultObservationEntity();
        o.setObservationId(UUID.randomUUID());
        o.setOrderId("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        o.setAnalyteCode("718-7");
        o.setAnalyteSystem("http://loinc.org");
        o.setAnalyteName(name);
        o.setValueNumeric(value);
        o.setUnit(unit);
        o.setRefRangeLow(new BigDecimal("12"));
        o.setRefRangeHigh(new BigDecimal("16"));
        o.setRefRangeText("12-16");
        o.setAbnormalFlag(flag);
        o.setCriticalFlag(critical);
        return o;
    }

    @Test
    @DisplayName("no observations: no-op, no HTTP")
    void emptyNoOp() {
        ButanoIntegration b = new ButanoIntegration(restTemplate, "http://localhost:8090", false);
        assertThat(b.createObservations("ORD-1", finalResult(), List.of())).isZero();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("POSTs a FHIR Observation per analyte with value/unit/refRange/interpretation")
    void postsObservations() {
        ButanoIntegration b = new ButanoIntegration(restTemplate, "http://localhost:8090", false);
        when(restTemplate.postForEntity(eq("http://localhost:8090/fhir/Observation"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("id", "Observation/1")));

        int written = b.createObservations("01ARZ3NDEKTSV4RRFFQ69G5FAV", finalResult(),
                List.of(obs("Haemoglobin", new BigDecimal("8.1"), "g/dL", "L", false)));

        assertThat(written).isEqualTo(1);
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), captor.capture(), eq(Map.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertThat(body).containsEntry("resourceType", "Observation").containsEntry("status", "final");
        assertThat(body.get("valueQuantity").toString()).contains("8.1").contains("g/dL")
                .contains("http://unitsofmeasure.org");
        assertThat(body.get("referenceRange").toString()).contains("12-16");
        // Body is a Map; toString renders entries as key=value — the abnormal flag maps to interpretation.
        assertThat(body.get("interpretation").toString()).contains("code=L");
    }
}
