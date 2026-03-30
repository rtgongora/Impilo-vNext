package zw.gov.mohcc.impilo.mushex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistry;
import zw.gov.mohcc.impilo.companion.consistency.ActionRegistryEntry;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * MUSHEX-specific Tech Companion configuration.
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "mushex.idempotency_keys", 24);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();

        // Claims
        registry.register(ActionRegistryEntry.classB("/mushex/v1/claims", "POST", "create_claim", 0));
        registry.register(ActionRegistryEntry.classC("/mushex/v1/claims/{id}", "GET", "get_claim"));
        registry.register(ActionRegistryEntry.classB("/mushex/v1/claims/{id}/submit", "POST", "submit_claim", 0));
        registry.register(ActionRegistryEntry.classA("/mushex/v1/claims/{id}/adjudication", "POST", "adjudicate_claim", true));
        registry.register(ActionRegistryEntry.classB("/mushex/v1/claims/{id}/dispute", "POST", "dispute_claim", 0));
        registry.register(ActionRegistryEntry.classB("/mushex/v1/claims/{id}/attachments", "POST", "add_claim_attachment", 0));

        // Settlements
        registry.register(ActionRegistryEntry.classA("/mushex/v1/settlements/run", "POST", "run_settlement", true));
        registry.register(ActionRegistryEntry.classC("/mushex/v1/settlements/{id}", "GET", "get_settlement"));
        registry.register(ActionRegistryEntry.classA("/mushex/v1/settlements/{id}/release-payouts", "POST", "release_payouts", true));

        // Payment Intents
        registry.register(ActionRegistryEntry.classB("/mushex/v1/payment-intents", "POST", "create_payment_intent", 0));
        registry.register(ActionRegistryEntry.classC("/mushex/v1/payment-intents/{id}", "GET", "get_payment_intent"));
        registry.register(ActionRegistryEntry.classA("/mushex/v1/payment-intents/{id}/cancel", "POST", "cancel_payment_intent", true));
        registry.register(ActionRegistryEntry.classB("/mushex/v1/payment-intents/{id}/issue-remittance-slip", "POST", "issue_remittance_slip", 0));
        registry.register(ActionRegistryEntry.classA("/mushex/v1/payment-intents/{id}/refund", "POST", "request_refund", true));
        registry.register(ActionRegistryEntry.classC("/mushex/v1/payment-intents/{id}/receipts", "GET", "get_receipts"));

        // Internal API — claims
        registry.register(ActionRegistryEntry.classB("/internal/v1/claims", "POST", "internal_create_claim", 0));
        registry.register(ActionRegistryEntry.classA("/internal/v1/claims/{id}/adjudicate", "POST", "internal_adjudicate_claim", true));
        registry.register(ActionRegistryEntry.classC("/internal/v1/claims/snapshot", "GET", "internal_claims_snapshot"));

        // Internal API — payments
        registry.register(ActionRegistryEntry.classA("/internal/v1/payments", "POST", "internal_create_payment", true));

        return registry;
    }
}
