package zw.gov.mohcc.impilo.telemonitoring.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringPlanService;
import zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringPlanRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the OROS enrolment seam end-to-end against the real plan engine (H2). The
 * consumer bean itself is {@code @Profile("!test")} (no broker in unit tests), so the
 * class is instantiated directly — the listener method body is what is under test.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrosEnrolmentConsumerTest {

    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringPlanRepository planRepository;
    @Autowired private MonitoringProgrammeRepository programmeRepository;

    private OrosEnrolmentConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrosEnrolmentConsumer(planService, planRepository, new ObjectMapper(), "TM-MON");
        if (programmeRepository.findByCode("HYPERTENSION").isEmpty()) {
            MonitoringProgrammeEntity p = new MonitoringProgrammeEntity();
            p.setCode("HYPERTENSION");
            p.setName("Hypertension");
            programmeRepository.save(p);
        }
    }

    private static String orderJson(String orderId, String orderType, String ziboOrderCode,
                                    String itemCode, String tenantId, String externalRefs) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"orderId\":\"").append(orderId).append("\",");
        sb.append("\"tenantId\":\"").append(tenantId).append("\",");
        sb.append("\"facilityId\":\"").append(UUID.randomUUID()).append("\",");
        sb.append("\"patientCpid\":\"CPID-77001\",");
        sb.append("\"orderType\":\"").append(orderType).append("\",");
        if (ziboOrderCode != null) {
            sb.append("\"ziboOrderCode\":\"").append(ziboOrderCode).append("\",");
        }
        if (externalRefs != null) {
            sb.append("\"externalRefs\":\"").append(externalRefs.replace("\"", "\\\"")).append("\",");
        }
        if (itemCode != null) {
            sb.append("\"items\":[{\"code\":\"").append(itemCode).append("\",\"quantity\":1}],");
        }
        sb.append("\"placedBy\":\"clinician-a\"}");
        return sb.toString();
    }

    @Test
    void monitoringOrderViaZiboCodeCreatesDraftPlanWithSpineRef() {
        String orderId = "01ORD" + UUID.randomUUID().toString().substring(0, 12);
        UUID tenant = UUID.randomUUID();
        consumer.consumeOrosOrder(orderJson(orderId, "OTHER", "TM-MON-HYPERTENSION", null,
                tenant.toString(), null));

        Optional<MonitoringPlanEntity> plan = planRepository.findByOrosOrderId(orderId);
        assertThat(plan).isPresent();
        assertThat(plan.get().getStatus()).isEqualTo(PlanStatus.DRAFT); // never self-activates
        assertThat(plan.get().getProgrammeCode()).isEqualTo("HYPERTENSION");
        assertThat(plan.get().getTenantId()).isEqualTo(tenant);
        assertThat(plan.get().getPatientCpid()).isEqualTo("CPID-77001");
    }

    @Test
    void monitoringOrderViaItemCodeIsAccepted() {
        String orderId = "01ORD" + UUID.randomUUID().toString().substring(0, 12);
        consumer.consumeOrosOrder(orderJson(orderId, "OTHER", null, "TM-MON-DIABETES",
                UUID.randomUUID().toString(), null));
        Optional<MonitoringPlanEntity> plan = planRepository.findByOrosOrderId(orderId);
        assertThat(plan).isPresent();
        assertThat(plan.get().getProgrammeCode()).isEqualTo("DIABETES");
    }

    @Test
    void discriminatorFiltersNonOtherAndNonMonitoringOrders() {
        String pharmacyOrder = "01ORD" + UUID.randomUUID().toString().substring(0, 12);
        consumer.consumeOrosOrder(orderJson(pharmacyOrder, "PHARMACY", "TM-MON-HYPERTENSION", null,
                UUID.randomUUID().toString(), null));
        assertThat(planRepository.findByOrosOrderId(pharmacyOrder)).isEmpty();

        String plainOther = "01ORD" + UUID.randomUUID().toString().substring(0, 12);
        consumer.consumeOrosOrder(orderJson(plainOther, "OTHER", "REF-GENERAL", "GEN-1",
                UUID.randomUUID().toString(), null));
        assertThat(planRepository.findByOrosOrderId(plainOther)).isEmpty();
    }

    @Test
    void replayIsIdempotentOnOrosOrderId() {
        String orderId = "01ORD" + UUID.randomUUID().toString().substring(0, 12);
        String message = orderJson(orderId, "OTHER", "TM-MON-HYPERTENSION", null,
                UUID.randomUUID().toString(), null);
        consumer.consumeOrosOrder(message);
        consumer.consumeOrosOrder(message);
        consumer.consumeOrosOrder(message);
        assertThat(planRepository.findByOrosOrderId(orderId)).isPresent();
        long count = planRepository.findAll().stream()
                .filter(p -> orderId.equals(p.getOrosOrderId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void suggestionProvenanceRidesExternalRefs() {
        String orderId = "01ORD" + UUID.randomUUID().toString().substring(0, 12);
        consumer.consumeOrosOrder(orderJson(orderId, "OTHER", "TM-MON-HYPERTENSION", null,
                UUID.randomUUID().toString(),
                "{\"suggestedBy\":\"NOMPILO-RISK-MODEL\",\"suggestedSystemVersion\":\"2026.07\"}"));
        MonitoringPlanEntity plan = planRepository.findByOrosOrderId(orderId).orElseThrow();
        assertThat(plan.getSuggestedBy()).isEqualTo("NOMPILO-RISK-MODEL");
        assertThat(plan.getSuggestedSystemVersion()).isEqualTo("2026.07");
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    void programmeCodeDerivation() {
        assertThat(consumer.deriveProgrammeCode("TM-MON-HYPERTENSION")).isEqualTo("HYPERTENSION");
        assertThat(consumer.deriveProgrammeCode("TM-MON_diabetes")).isEqualTo("DIABETES");
        assertThat(consumer.deriveProgrammeCode("TM-MON")).isEqualTo("UNSPECIFIED");
    }

    @Test
    void malformedJsonIsLoggedNotThrown() {
        consumer.consumeOrosOrder("{not-json");
        // No exception, no plan.
    }
}
