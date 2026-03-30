package zw.gov.mohcc.impilo.oros.config;

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
        return new JdbcIdempotencyRepository(jdbc, "oros_idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        registry.register(ActionRegistryEntry.classB("/v1/orders", "POST", "create_order", 0));
        registry.register(ActionRegistryEntry.classC("/v1/orders/{orderId}", "GET", "get_order"));
        registry.register(ActionRegistryEntry.classC("/v1/orders/patient/{cpid}", "GET", "get_patient_orders"));
        registry.register(ActionRegistryEntry.classA("/v1/orders/{orderId}/cancel", "POST", "cancel_order", true));

        registry.register(ActionRegistryEntry.classB("/v1/orders/{orderId}/results", "POST", "submit_result", 0));
        registry.register(ActionRegistryEntry.classC("/v1/orders/{orderId}/results", "GET", "get_results"));
        registry.register(ActionRegistryEntry.classA("/v1/orders/{orderId}/results/{resultId}/mark-critical", "POST", "mark_critical", true));

        registry.register(ActionRegistryEntry.classC("/v1/worklists", "GET", "get_worklist"));
        registry.register(ActionRegistryEntry.classB("/v1/orders/{orderId}/accept", "POST", "accept_order", 0));
        registry.register(ActionRegistryEntry.classA("/v1/orders/{orderId}/reject", "POST", "reject_order", true));

        return registry;
    }
}
