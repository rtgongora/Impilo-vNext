package zw.gov.mohcc.impilo.pct.config;

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
        return new JdbcIdempotencyRepository(jdbc, "pct.idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        registry.register(ActionRegistryEntry.classB("/v1/journeys/start", "POST", "start_journey", 0));
        registry.register(ActionRegistryEntry.classC("/v1/journeys/{id}", "GET", "get_journey"));
        registry.register(ActionRegistryEntry.classC("/v1/journeys/patient/{cpid}", "GET", "get_patient_journeys"));
        registry.register(ActionRegistryEntry.classB("/v1/journeys/{id}/triage", "POST", "triage_journey", 0));
        registry.register(ActionRegistryEntry.classB("/v1/journeys/{id}/route", "POST", "route_journey", 0));

        registry.register(ActionRegistryEntry.classA("/v1/journeys/{id}/discharge/start", "POST", "start_discharge", true));
        registry.register(ActionRegistryEntry.classC("/discharge/{caseId}/status", "GET", "get_discharge_status"));
        registry.register(ActionRegistryEntry.classB("/discharge/{caseId}/clear-blocker", "POST", "clear_discharge_blocker", 0));
        registry.register(ActionRegistryEntry.classA("/discharge/{caseId}/complete", "POST", "complete_discharge", true));

        registry.register(ActionRegistryEntry.classC("/v1/queues", "GET", "list_queues"));
        registry.register(ActionRegistryEntry.classB("/v1/queues/{queueId}/items", "POST", "enqueue_item", 0));
        registry.register(ActionRegistryEntry.classC("/v1/queues/{queueId}/items", "GET", "get_queue_items"));

        registry.register(ActionRegistryEntry.classB("/v1/encounters", "POST", "create_encounter", 0));
        registry.register(ActionRegistryEntry.classC("/v1/encounters/{id}", "GET", "get_encounter"));
        registry.register(ActionRegistryEntry.classA("/v1/encounters/{id}/complete", "POST", "complete_encounter", true));

        registry.register(ActionRegistryEntry.classB("/v1/admissions", "POST", "create_admission", 0));
        registry.register(ActionRegistryEntry.classC("/v1/admissions/{id}", "GET", "get_admission"));
        registry.register(ActionRegistryEntry.classA("/v1/admissions/{admissionId}/discharge", "POST", "discharge_admission", true));

        return registry;
    }
}
