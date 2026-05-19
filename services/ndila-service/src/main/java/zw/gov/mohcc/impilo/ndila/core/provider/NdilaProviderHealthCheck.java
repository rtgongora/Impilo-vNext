package zw.gov.mohcc.impilo.ndila.core.provider;

public interface NdilaProviderHealthCheck {

    HealthSnapshot health(String providerName, String operationType);

    record HealthSnapshot(
            String providerName,
            String operationType,
            boolean healthy,
            int consecutiveFailures,
            int avgLatencyMs,
            String lastError
    ) {}
}
