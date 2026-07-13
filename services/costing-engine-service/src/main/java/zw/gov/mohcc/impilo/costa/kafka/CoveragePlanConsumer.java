package zw.gov.mohcc.impilo.costa.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.InsurancePlanEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.InsurancePlanRepository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Keeps COSTA's insurance-plan registry aware of plans created at the coverage
 * SoR. Coverage-service plans carry NO money-split terms (coverage_pct / copay /
 * deductible are COSTA-governed billing configuration), so this consumer NEVER
 * invents terms: a new plan lands as a {@code PENDING_TERMS} placeholder that
 * {@code applyCoverage}'s ACTIVE-only lookup still refuses (the bill records
 * {@code PLAN_NOT_CONFIGURED} and splits 100% patient — honest). The placeholder
 * exists so finance has a durable, queryable work item instead of discovering
 * the gap when a patient hits the counter.
 */
@Component
public class CoveragePlanConsumer {

    private static final Logger log = LoggerFactory.getLogger(CoveragePlanConsumer.class);

    /** Placeholder status: visible to finance, invisible to the ACTIVE-only split lookup. */
    static final String STATUS_PENDING_TERMS = "PENDING_TERMS";

    private final InsurancePlanRepository insurancePlanRepository;
    private final ObjectMapper objectMapper;

    public CoveragePlanConsumer(InsurancePlanRepository insurancePlanRepository, ObjectMapper objectMapper) {
        this.insurancePlanRepository = insurancePlanRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "coverage.plans", groupId = "costa-costing-engine")
    @Transactional
    public void onCoveragePlanEvent(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String planCode = text(event, "plan_code", "planCode");
            String tenantStr = text(event, "tenantId", "tenant_id");
            if (planCode == null || planCode.isBlank() || tenantStr == null) {
                log.warn("coverage.plans event without plan_code/tenantId — skipping: {}", message);
                ack.acknowledge();
                return;
            }
            UUID tenantId = UUID.fromString(tenantStr);

            boolean known = insurancePlanRepository
                    .findByTenantIdAndPlanCodeAndStatus(tenantId, planCode, "ACTIVE").isPresent()
                    || insurancePlanRepository
                    .findByTenantIdAndPlanCodeAndStatus(tenantId, planCode, STATUS_PENDING_TERMS).isPresent();
            if (!known) {
                InsurancePlanEntity placeholder = new InsurancePlanEntity();
                placeholder.setTenantId(tenantId);
                placeholder.setPlanCode(planCode);
                placeholder.setPlanName(orDefault(text(event, "plan_name", "planName"), planCode));
                placeholder.setInsurerName(orDefault(text(event, "payer_id", "payerId"), "UNKNOWN_PAYER"));
                placeholder.setStatus(STATUS_PENDING_TERMS);
                // Terms deliberately zeroed: money splits are governed COSTA config,
                // never inferred from a plan-created event.
                placeholder.setCoveragePct(BigDecimal.ZERO);
                placeholder.setCopayPct(BigDecimal.ZERO);
                insurancePlanRepository.save(placeholder);
                log.warn("COVERAGE PLAN {} created at the SoR but has NO billing terms in COSTA — "
                        + "saved PENDING_TERMS placeholder; finance must configure the split terms "
                        + "before coverage can apply.", planCode);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process coverage.plans", e);
            // Do NOT ack: bounded retry then costa.money.dlq — never drop a money event.
            throw new MoneyEventProcessingException("coverage.plans", e);
        }
    }

    private static String orDefault(String v, String fallback) {
        return v != null && !v.isBlank() ? v : fallback;
    }

    private String text(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }
}
