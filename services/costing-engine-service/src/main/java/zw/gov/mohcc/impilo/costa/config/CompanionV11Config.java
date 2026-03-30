package zw.gov.mohcc.impilo.costa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "costa_idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        registry.register(ActionRegistryEntry.classB("/costa/v1/bills/draft", "POST", "draft_bill", 0));
        registry.register(ActionRegistryEntry.classC("/costa/v1/bills/{id}", "GET", "get_bill"));
        registry.register(ActionRegistryEntry.classB("/costa/v1/bills/{id}/lines", "POST", "add_charge_line", 0));
        registry.register(ActionRegistryEntry.classB("/costa/v1/bills/{id}/recompute", "POST", "recompute_bill", 0));
        registry.register(ActionRegistryEntry.classB("/costa/v1/bills/{id}/submit-approval", "POST", "submit_bill_approval", 0));
        registry.register(ActionRegistryEntry.classA("/costa/v1/bills/{id}/approve", "POST", "approve_bill", true));
        registry.register(ActionRegistryEntry.classA("/costa/v1/bills/{id}/finalize", "POST", "finalize_bill", true));
        registry.register(ActionRegistryEntry.classA("/costa/v1/bills/{id}/issue-invoice", "POST", "issue_invoice", true));
        registry.register(ActionRegistryEntry.classA("/costa/v1/bills/{id}/create-payment-intent", "POST", "create_payment_intent", true));
        registry.register(ActionRegistryEntry.classA("/costa/v1/bills/{id}/refund", "POST", "refund_bill", true));

        registry.register(ActionRegistryEntry.classC("/costa/v1/tariffs", "GET", "list_tariffs"));

        return registry;
    }
}
