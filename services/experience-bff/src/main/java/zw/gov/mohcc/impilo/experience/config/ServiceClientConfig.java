package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;

/**
 * Configures RestTemplate beans for communicating with sovereign platform services.
 *
 * <p>Each RestTemplate is pre-configured with an interceptor that forwards the
 * v1.1 trust headers (X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID,
 * Authorization) from the inbound request to the outbound service call. This
 * ensures the sovereign service receives the same trust context as the BFF.</p>
 */
@Configuration
@EnableConfigurationProperties(ServiceClientConfig.ServiceEndpoints.class)
public class ServiceClientConfig {

    @ConfigurationProperties(prefix = "impilo.services")
    public record ServiceEndpoints(
            String pctBaseUrl,
            String orosBaseUrl,
            String pharmacyBaseUrl,
            String butanoBaseUrl,
            String msikaBaseUrl,
            String msikaFlowBaseUrl,
            String mushexBaseUrl,
            String vitoBaseUrl,
            String tusoBaseUrl,
            String varapiBaseUrl,
            String documentStoreBaseUrl,
            String costaBaseUrl,
            String coverageBaseUrl,
            String surveillanceBaseUrl,
            String campaignsBaseUrl,
            String indawoBaseUrl,
            String dataGovernanceBaseUrl,
            String landelaBaseUrl,
            String notificationBaseUrl,
            String credentialBaseUrl,
            // Health OS §10: Interoperability
            String fhirBaseUrl,
            String fhirGatewayBaseUrl,
            // Health OS §12: Governed Knowledge
            String searchBaseUrl,
            // Health OS §11: Extension Points
            String formsBaseUrl,
            String rulesBaseUrl,
            // Health OS §5: Workflow
            String workflowBaseUrl,
            // Health OS §13: Conversational & Guidance
            String guidanceBaseUrl,
            // Integration / cross-tenant routing (integration-hub-service)
            String integrationHubBaseUrl,
            // Health OS §6: Privacy by Architecture — Data Access Governance
            String dagsBaseUrl,
            // Health OS §7: Trust Layer (TSHEPO cluster)
            String tshepoAuthzBaseUrl,
            String tshepoConsentBaseUrl,
            String tshepoAuditBaseUrl,
            String tshepoIdentityBaseUrl,
            String tshepoKeysBaseUrl,
            String tshepoOfflineBaseUrl,
            String ziboBaseUrl,
            String ubomiBaseUrl,
            String inpatientBaseUrl,
            String pacsBaseUrl,
            String communityBaseUrl,
            String simbaBaseUrl,
            String musheWalletBaseUrl,
            String inventoryBaseUrl,
            String assetRegistryBaseUrl,
            String inventoryElmisBaseUrl,
            /** National Data Repository — canonical datasets & query */
            String ndrNationalBaseUrl,
            /** NDR ring — bronze/gold ingest & query */
            String ndrQueryBaseUrl,
            String dataPipelineBaseUrl,
            String reportingBaseUrl,
            String dataWarehouseBaseUrl,
            String dataIngestionBaseUrl,
            String aiModelRegistryBaseUrl,
            String generalLedgerBaseUrl,
            String hrPayrollBaseUrl,
            String procurementBaseUrl,
            String channelsBaseUrl,
            String dispatchBaseUrl,
            String supportBaseUrl,
            String wellnessBaseUrl,
            String workforceGovernanceBaseUrl
    ) {
        public ServiceEndpoints {
            if (pctBaseUrl == null) pctBaseUrl = "http://localhost:8088";
            if (orosBaseUrl == null) orosBaseUrl = "http://localhost:8089";
            if (pharmacyBaseUrl == null) pharmacyBaseUrl = "http://localhost:8096";
            if (butanoBaseUrl == null) butanoBaseUrl = "http://localhost:8090";
            if (msikaBaseUrl == null) msikaBaseUrl = "http://localhost:8086";
            if (msikaFlowBaseUrl == null) msikaFlowBaseUrl = "http://localhost:8100";
            if (mushexBaseUrl == null) mushexBaseUrl = "http://localhost:8102";
            if (vitoBaseUrl == null) vitoBaseUrl = "http://localhost:8082";
            if (tusoBaseUrl == null) tusoBaseUrl = "http://localhost:8084";
            if (varapiBaseUrl == null) varapiBaseUrl = "http://localhost:8083";
            if (documentStoreBaseUrl == null) documentStoreBaseUrl = "http://localhost:8093";
            if (costaBaseUrl == null) costaBaseUrl = "http://localhost:8101";
            if (coverageBaseUrl == null) coverageBaseUrl = "http://localhost:8140";
            if (surveillanceBaseUrl == null) surveillanceBaseUrl = "http://localhost:8180";
            if (campaignsBaseUrl == null) campaignsBaseUrl = "http://localhost:8190";
            if (indawoBaseUrl == null) indawoBaseUrl = "http://localhost:8150";
            if (dataGovernanceBaseUrl == null) dataGovernanceBaseUrl = "http://localhost:8220";
            if (landelaBaseUrl == null) landelaBaseUrl = "http://localhost:8092";
            if (notificationBaseUrl == null) notificationBaseUrl = "http://localhost:8200";
            if (credentialBaseUrl == null) credentialBaseUrl = "http://localhost:8094";
            if (fhirBaseUrl == null) fhirBaseUrl = "http://localhost:8090/fhir";
            if (fhirGatewayBaseUrl == null) fhirGatewayBaseUrl = "http://localhost:8091";
            if (searchBaseUrl == null) searchBaseUrl = "http://localhost:8230";
            if (formsBaseUrl == null) formsBaseUrl = "http://localhost:8240";
            if (rulesBaseUrl == null) rulesBaseUrl = "http://localhost:8241";
            if (workflowBaseUrl == null) workflowBaseUrl = "http://localhost:8250";
            if (guidanceBaseUrl == null) guidanceBaseUrl = "http://localhost:8260";
            if (integrationHubBaseUrl == null) integrationHubBaseUrl = "http://localhost:8110";
            if (dagsBaseUrl == null) dagsBaseUrl = "http://localhost:8170";
            if (tshepoAuthzBaseUrl == null) tshepoAuthzBaseUrl = "http://localhost:8081";
            if (tshepoConsentBaseUrl == null) tshepoConsentBaseUrl = "http://localhost:8182";
            if (tshepoAuditBaseUrl == null) tshepoAuditBaseUrl = "http://localhost:8183";
            if (tshepoIdentityBaseUrl == null) tshepoIdentityBaseUrl = "http://localhost:8181";
            if (tshepoKeysBaseUrl == null) tshepoKeysBaseUrl = "http://localhost:8184";
            if (tshepoOfflineBaseUrl == null) tshepoOfflineBaseUrl = "http://localhost:8185";
            if (ziboBaseUrl == null) ziboBaseUrl = "http://localhost:8085";
            if (ubomiBaseUrl == null) ubomiBaseUrl = "http://localhost:8087";
            if (inpatientBaseUrl == null) inpatientBaseUrl = "http://localhost:8121";
            if (pacsBaseUrl == null) pacsBaseUrl = "http://localhost:8113";
            if (communityBaseUrl == null) communityBaseUrl = "http://localhost:8122";
            if (simbaBaseUrl == null) simbaBaseUrl = "http://localhost:8125";
            if (musheWalletBaseUrl == null) musheWalletBaseUrl = "http://localhost:8126";
            if (inventoryBaseUrl == null) inventoryBaseUrl = "http://localhost:8098";
            if (assetRegistryBaseUrl == null) assetRegistryBaseUrl = "http://localhost:8310";
            if (inventoryElmisBaseUrl == null) inventoryElmisBaseUrl = "http://localhost:8108";
            if (ndrNationalBaseUrl == null) ndrNationalBaseUrl = "http://localhost:8152";
            if (ndrQueryBaseUrl == null) ndrQueryBaseUrl = "http://localhost:8232";
            if (dataPipelineBaseUrl == null) dataPipelineBaseUrl = "http://localhost:8215";
            if (reportingBaseUrl == null) reportingBaseUrl = "http://localhost:8176";
            if (dataWarehouseBaseUrl == null) dataWarehouseBaseUrl = "http://localhost:8233";
            if (dataIngestionBaseUrl == null) dataIngestionBaseUrl = "http://localhost:8210";
            if (aiModelRegistryBaseUrl == null) aiModelRegistryBaseUrl = "http://localhost:8280";
            if (generalLedgerBaseUrl == null) generalLedgerBaseUrl = "http://localhost:8281";
            if (hrPayrollBaseUrl == null) hrPayrollBaseUrl = "http://localhost:8282";
            if (procurementBaseUrl == null) procurementBaseUrl = "http://localhost:8283";
            if (channelsBaseUrl == null) channelsBaseUrl = "http://localhost:8290";
            if (dispatchBaseUrl == null) dispatchBaseUrl = "http://localhost:8291";
            if (supportBaseUrl == null) supportBaseUrl = "http://localhost:8292";
            if (wellnessBaseUrl == null) wellnessBaseUrl = "http://localhost:8293";
            if (workforceGovernanceBaseUrl == null) workforceGovernanceBaseUrl = "http://localhost:8165";
        }
    }

    /**
     * All-null service endpoints for unit tests — the record compact constructor applies localhost defaults
     * (including {@code workforceGovernanceBaseUrl}).
     */
    public static ServiceEndpoints testServiceEndpoints() {
        return new ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
    }

    /**
     * Interceptor that copies v1.1 trust headers from the current inbound
     * HTTP request onto every outbound RestTemplate call.
     */
    @Bean
    public ClientHttpRequestInterceptor trustHeaderForwardingInterceptor() {
        return (request, body, execution) -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest inbound = attrs.getRequest();
                forwardHeader(inbound, request, CompanionHeaders.TENANT_ID);
                forwardHeader(inbound, request, CompanionHeaders.POD_ID);
                forwardHeader(inbound, request, CompanionHeaders.REQUEST_ID);
                forwardHeader(inbound, request, CompanionHeaders.CORRELATION_ID);
                forwardHeader(inbound, request, CompanionHeaders.AUTHORIZATION);
                forwardHeader(inbound, request, CompanionHeaders.ACTOR_ID);
                forwardHeader(inbound, request, CompanionHeaders.ACTOR_TYPE);
                forwardHeader(inbound, request, CompanionHeaders.PROVIDER_ID);
                forwardHeader(inbound, request, CompanionHeaders.PURPOSE_OF_USE);
                forwardHeader(inbound, request, CompanionHeaders.DEVICE_FINGERPRINT);
                forwardHeader(inbound, request, CompanionHeaders.ASSURANCE_LEVEL);
                forwardHeader(inbound, request, CompanionHeaders.FACILITY_ID);
                forwardHeader(inbound, request, CompanionHeaders.TUSO_FACILITY_ID);
                forwardHeader(inbound, request, CompanionHeaders.WORKSPACE_ID);
                forwardHeader(inbound, request, CompanionHeaders.SHIFT_ID);
                forwardHeader(inbound, request, CompanionHeaders.IDEMPOTENCY_KEY);
                forwardHeader(inbound, request, CompanionHeaders.CLIENT_TIMEOUT_MS);
                forwardHeader(inbound, request, CompanionHeaders.PATIENT_SHARE_GRANT_ID);
                forwardHeader(inbound, request, CompanionHeaders.VITO_CONTRIBUTION_ID);
                forwardHeader(inbound, request, CompanionHeaders.TEMPORARY_PROVIDER_PUBLIC_ID);
                forwardHeader(inbound, request, CompanionHeaders.PATIENT_SHARE_CORRELATION_ID);
                forwardHeader(inbound, request, CompanionHeaders.EXTERNAL_PROVIDER_TRUST_LEVEL);
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    public RestTemplate serviceRestTemplate(ClientHttpRequestInterceptor trustHeaderForwardingInterceptor) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(List.of(trustHeaderForwardingInterceptor));
        return restTemplate;
    }

    private static void forwardHeader(HttpServletRequest inbound,
                                       org.springframework.http.HttpRequest outbound,
                                       String headerName) {
        String value = inbound.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            outbound.getHeaders().set(headerName, value);
        }
    }
}
