package zw.gov.mohcc.impilo.oros.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @Mock private FhirGatewayClient gateway;

    private static FhirGatewayClient.Result success(String id) {
        return new FhirGatewayClient.Result(FhirGatewayClient.Outcome.SUCCESS, id, "1", "SUCCESS");
    }

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
    @DisplayName("no observations: no-op, nothing forwarded")
    void emptyNoOp() {
        ButanoIntegration b = new ButanoIntegration(gateway, orderRepoWithCpid(), false);
        assertThat(b.createObservations("ORD-1", finalResult(), List.of())).isZero();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("forwards a FHIR Observation per analyte with value/unit/refRange/interpretation")
    void forwardsObservations() {
        ButanoIntegration b = new ButanoIntegration(gateway, orderRepoWithCpid(), false);
        when(gateway.forward(eq("Observation"), eq("CREATE"), any(), any(), any(), any(), any(), any()))
                .thenReturn(success("1"));

        int written = b.createObservations("01ARZ3NDEKTSV4RRFFQ69G5FAV", finalResult(),
                List.of(obs("Haemoglobin", new BigDecimal("8.1"), "g/dL", "L", false)));

        assertThat(written).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forward(eq("Observation"), eq("CREATE"), captor.capture(), any(), any(),
                any(), any(), any());
        Map<String, Object> body = captor.getValue();
        assertThat(body).containsEntry("resourceType", "Observation").containsEntry("status", "final");
        assertThat(body.get("valueQuantity").toString()).contains("8.1").contains("g/dL")
                .contains("http://unitsofmeasure.org");
        assertThat(body.get("referenceRange").toString()).contains("12-16");
        // Body is a Map; toString renders entries as key=value — the abnormal flag maps to interpretation.
        assertThat(body.get("interpretation").toString()).contains("code=L");
    }

    @Test
    @DisplayName("an outage abandons the remaining analytes; a refusal does not")
    void anOutageStopsButARefusalDoesNot() {
        // A full blood count is 20+ forwards. If the gateway is unreachable the next analyte fails
        // identically, so grinding through them all buys nothing. A consent refusal is a
        // per-resource verdict, and the count must stay honest either way.
        ButanoIntegration b = new ButanoIntegration(gateway, orderRepoWithCpid(), false);
        var panel = List.of(
                obs("Haemoglobin", new BigDecimal("8.1"), "g/dL", "L", false),
                obs("Platelets", new BigDecimal("150"), "10*9/L", null, false),
                obs("White cells", new BigDecimal("6.0"), "10*9/L", null, false));

        when(gateway.forward(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(success("1"))
                .thenReturn(new FhirGatewayClient.Result(
                        FhirGatewayClient.Outcome.UNREACHABLE, null, null, "gateway down"));

        assertThat(b.createObservations("01ARZ3NDEKTSV4RRFFQ69G5FAV", finalResult(), panel))
                .describedAs("one written, then abandoned — not reported as three")
                .isEqualTo(1);
        verify(gateway, times(2)).forward(any(), any(), any(), any(), any(), any(), any(), any());

        clearInvocations(gateway);
        when(gateway.forward(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FhirGatewayClient.Result(
                        FhirGatewayClient.Outcome.CONSENT_DENIED, null, "9", "consent=DENY"))
                .thenReturn(success("2"))
                .thenReturn(success("3"));

        assertThat(b.createObservations("01ARZ3NDEKTSV4RRFFQ69G5FAV", finalResult(), panel))
                .describedAs("a refusal on one analyte does not silence the rest")
                .isEqualTo(2);
        verify(gateway, times(3)).forward(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * An order repository that resolves the subject, so these tests exercise the real path.
     *
     * <p>DiagnosticReport and Observation used to carry no subject at all — the CPID is on
     * OrderEntity and neither method received it. It is resolved from the order id now.</p>
     */
    private static zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository orderRepoWithCpid() {
        zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository repo = org.mockito.Mockito.mock(zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository.class);
        zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity order =
                new zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity();
        order.setPatientCpid("c08ba747-26ff-4f19-b712-76561505e274");
        org.mockito.Mockito.lenient()
                .when(repo.findByOrderId(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.of(order));
        return repo;
    }
}
