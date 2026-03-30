package zw.gov.mohcc.impilo.inpatient.config;

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
        return new JdbcIdempotencyRepository(jdbc, "inpatient.idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        registry.register(ActionRegistryEntry.classC("/internal/v1/admissions", "GET", "list_admissions"));
        registry.register(ActionRegistryEntry.classC("/internal/v1/admissions/{admissionRef}", "GET", "get_admission"));
        registry.register(ActionRegistryEntry.classB("/internal/v1/admissions", "POST", "create_admission", 0));
        registry.register(ActionRegistryEntry.classB("/internal/v1/admissions/{admissionRef}/transfer", "POST", "transfer_patient", 0));
        registry.register(ActionRegistryEntry.classA("/internal/v1/admissions/{admissionRef}/discharge", "POST", "discharge_patient", true));

        return registry;
    }
}
