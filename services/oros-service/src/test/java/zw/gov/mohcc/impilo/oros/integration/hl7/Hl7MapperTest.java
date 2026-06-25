package zw.gov.mohcc.impilo.oros.integration.hl7;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.oros.core.ExternalOrderIntake;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the HL7 v2 mappers — pure parse/build against the real HAPI library, no MLLP.
 */
class Hl7MapperTest {

    private static HapiContext ctx;

    @BeforeAll
    static void setup() {
        ctx = new DefaultHapiContext();
    }

    @AfterAll
    static void teardown() throws Exception {
        ctx.close();
    }

    @Test
    @DisplayName("ORM^O01 parses into a normalized external intake (CPID, code, placer ref)")
    void parsesOrm() {
        // Minimal v2.5 ORM^O01: MSH, PID (id in PID-3), ORC (placer in ORC-2), OBR (service in OBR-4).
        String orm = String.join("\r",
                "MSH|^~\\&|EXTLIS|EXTFAC|IMPILO|MOH|20260101120000||ORM^O01|MSG1|P|2.5",
                "PID|1||CPID-9^^^EXTLIS^MR||Doe^Jane",
                "ORC|NW|PLACER-123",
                "OBR|1|PLACER-123||CT-HEAD^CT Head^LN");

        ExternalOrderIntake input = new Hl7OrmMapper(ctx).parse(orm);

        assertThat(input.patientCpid()).isEqualTo("CPID-9");
        assertThat(input.code()).isEqualTo("CT-HEAD");
        assertThat(input.displayName()).isEqualTo("CT Head");
        assertThat(input.orderType()).isEqualTo(OrderType.IMAGING);
        assertThat(input.externalRef()).isEqualTo("hl7:PLACER-123");
    }

    @Test
    @DisplayName("ORU^R01 is built with MSH/PID/OBR/OBX carrying CPID, accession, and the impression")
    void buildsOru() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORD-1");
        order.setPatientCpid("CPID-9");
        order.setAccessionNumber("ACC-2026-AB-1");
        ResultEntity result = new ResultEntity();
        result.setZiboResultCodes("CT-HEAD");
        result.setImpression("No acute intracranial abnormality.");

        String encoded = new Hl7OruMapper(ctx).buildOru(order, result);

        assertThat(encoded).contains("MSH|").contains("ORU^R01");
        assertThat(encoded).contains("CPID-9");
        assertThat(encoded).contains("ACC-2026-AB-1");
        assertThat(encoded).contains("No acute intracranial abnormality.");
        assertThat(encoded).contains("OBX|");
    }

    @Test
    @DisplayName("ORU^R01 emits one OBX per structured observation with value/unit/range/flag")
    void buildsOruWithObservations() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORD-2");
        order.setPatientCpid("CPID-9");
        order.setAccessionNumber("ACC-2026-AB-2");
        ResultEntity result = new ResultEntity();
        result.setZiboResultCodes("FBC");

        var hb = new zw.gov.mohcc.impilo.oros.persistence.entity.ResultObservationEntity();
        hb.setAnalyteCode("718-7");
        hb.setAnalyteName("Haemoglobin");
        hb.setValueNumeric(new java.math.BigDecimal("8.1"));
        hb.setUnit("g/dL");
        hb.setRefRangeText("12-16");
        hb.setAbnormalFlag("L");

        var k = new zw.gov.mohcc.impilo.oros.persistence.entity.ResultObservationEntity();
        k.setAnalyteCode("2823-3");
        k.setAnalyteName("Potassium");
        k.setValueNumeric(new java.math.BigDecimal("7.1"));
        k.setUnit("mmol/L");
        k.setRefRangeText("3.5-5.1");
        k.setCriticalFlag(true);

        String encoded = new Hl7OruMapper(ctx).buildOru(order, result, java.util.List.of(hb, k));

        assertThat(encoded).contains("ORU^R01");
        // Two distinct OBX segments, one per analyte, with numeric value type and units.
        assertThat(encoded).contains("OBX|1|NM|718-7^Haemoglobin").contains("8.1").contains("g/dL").contains("12-16");
        assertThat(encoded).contains("OBX|2|NM|2823-3^Potassium").contains("7.1").contains("mmol/L");
        // Critical potassium maps to an abnormal flag (AA when none supplied).
        assertThat(encoded).contains("AA");
    }
}
