package zw.gov.mohcc.impilo.mushex.integration;

/**
 * Mesh check against coverage-service for payer–provider contracting.
 */
public interface ProviderContractClient {

    /**
     * @return true if coverage-service reports at least one matching contract in ACTIVE status
     */
    boolean hasActiveContract(String tenantId, String providerId, String payerId);
}
