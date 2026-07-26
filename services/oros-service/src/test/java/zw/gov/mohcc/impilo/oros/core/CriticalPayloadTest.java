package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The critical-result event payload must say which episode of care the result belongs to.
 *
 * <p><b>Why this exists.</b> {@code criticalPayload} projected the ordering context — tenant,
 * patient, requester, accession — but omitted {@code encounterRef}, even though the field has always
 * existed on {@link OrderEntity}. A consumer could therefore tell that a critical result had been
 * flagged and for whom, but not which encounter it belonged to, which is the one thing needed to
 * close the loop on the right episode of care. The ED lane worked around it by keeping its own
 * correlation table ({@code pct.ed_diagnostic_order}); that table is the right place to track ED
 * acknowledgement either way, but it should not have been the only way to answer this question.
 */
class CriticalPayloadTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void payloadCarriesTheEncounterTheResultBelongsTo() {
        OrderEntity order = order();
        order.setEncounterRef("enc-4412");

        Map<String, Object> payload = ReportService.criticalPayload(order, result());

        assertThat(payload).containsEntry("encounterRef", "enc-4412");
    }

    @Test
    void anOrderWithNoEncounterReportsTheAbsenceRatherThanOmittingTheKey() {
        // The key must be present-and-null rather than missing, so a consumer can distinguish
        // "this order has no encounter" from "this producer is too old to tell me".
        Map<String, Object> payload = ReportService.criticalPayload(order(), result());

        assertThat(payload).containsKey("encounterRef");
        assertThat(payload.get("encounterRef")).isNull();
    }

    @Test
    void theExistingOrderContextIsStillProjected() {
        OrderEntity order = order();
        order.setEncounterRef("enc-1");

        Map<String, Object> payload = ReportService.criticalPayload(order, result());

        assertThat(payload)
                .containsEntry("orderId", "ORD-1")
                .containsEntry("patientCpid", "cpid-9")
                .containsEntry("accessionNumber", "ACC-1")
                .containsEntry("criticalReason", "Potassium 7.1 mmol/L")
                .containsEntry("tenantId", TENANT.toString());
    }

    private static OrderEntity order() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORD-1");
        order.setTenantId(TENANT);
        order.setPatientCpid("cpid-9");
        order.setAccessionNumber("ACC-1");
        order.setPlacedBy("provider-1");
        return order;
    }

    private static ResultEntity result() {
        ResultEntity result = new ResultEntity();
        result.setResultId(UUID.fromString("11111111-0000-4000-8000-000000000001"));
        result.setOrderId("ORD-1");
        result.setCriticalReason("Potassium 7.1 mmol/L");
        result.setReportedBy("lab-tech-1");
        return result;
    }
}
