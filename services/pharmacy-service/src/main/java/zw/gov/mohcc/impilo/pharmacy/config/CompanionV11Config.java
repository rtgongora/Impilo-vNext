package zw.gov.mohcc.impilo.pharmacy.config;

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
        return new JdbcIdempotencyRepository(jdbc, "pharmacy.idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        registry.register(ActionRegistryEntry.classC("/v1/dispense-orders/{id}", "GET", "get_dispense_order"));
        registry.register(ActionRegistryEntry.classC("/v1/dispense-orders/patient/{cpid}", "GET", "get_patient_dispense_orders"));
        registry.register(ActionRegistryEntry.classB("/v1/dispense-orders/{id}/accept", "POST", "accept_dispense_order", 0));
        registry.register(ActionRegistryEntry.classB("/v1/dispense-orders/{id}/pick", "POST", "pick_dispense_order", 0));
        registry.register(ActionRegistryEntry.classB("/v1/dispense-orders/{id}/partial", "POST", "partial_dispense", 0));
        registry.register(ActionRegistryEntry.classA("/v1/dispense-orders/{id}/complete", "POST", "complete_dispense", true));
        registry.register(ActionRegistryEntry.classB("/v1/dispense-orders/{id}/backorder", "POST", "backorder_dispense", 0));

        registry.register(ActionRegistryEntry.classC("/v1/worklists", "GET", "get_worklist"));

        return registry;
    }
}
