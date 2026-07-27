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
    private static final String X_SERVICE_ID = "X-Service-Id";
    private static final String X_SERVICE_NAME = "X-Service-Name";
    private static final String X_SERVICE_VERSION = "X-Service-Version";
    private static final String X_REQUEST_SOURCE = "X-Request-Source";
    private static final String X_EXTERNAL_APP_ID = "X-External-App-Id";
    private static final String X_INTEGRATION_TYPE = "X-Integration-Type";
    private static final String X_INTEGRATION_VERSION = "X-Integration-Version";
    private static final String X_REQUEST_SIGNATURE = "X-Request-Signature";
    private static final String X_AI_SKILL_ID = "X-AI-Skill-Id";
    private static final String X_AI_MODEL_REF = "X-AI-Model-Ref";

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
            /** Mvumo — sovereign Ring-0 consent orchestration (same class as Tshepo); BFF {@code /internal/v1/mvumo/**}. */
            String mvumoBaseUrl,
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
            String workforceGovernanceBaseUrl,
            /** vashandi-workforce-service — operational workforce SoR (roster, attendance, leave, access risk) */
            String vashandiBaseUrl,
            /** khuluma-service — Comms Hub (conversations, presence, calls, realtime gateway) */
            String khulumaBaseUrl,
            /** scheduling-service MVP — slot templates + holds (distinct default port from inpatient-service) */
            String schedulingServiceBaseUrl,
            /** booking-service — sovereign Booking + Appointment aggregates (port 8265) */
            String bookingBaseUrl,
            /** msika-apps-service — Health OS Capability Marketplace (Msika Apps). */
            String msikaAppsBaseUrl,
            /** Ndila — geospatial intelligence, routing, tiles (port 8155). */
            String ndilaBaseUrl,
            /** Nhume — dispatch, delivery, fleet (port 8210). */
            String nhumeBaseUrl,
            /** MADI — blood donation, blood bank, transfusion (port 8300). */
            String madiBaseUrl,
            /** Impilo Live — live events, webinars, broadcasts (port 8380). */
            String liveBaseUrl,
            /** analytics-pipeline-service — telemedicine lifecycle analytics (port 8365). */
            String analyticsPipelineBaseUrl,
            /** iot-ingestion-service — device registry + telemetry (port 8330). */
            String iotIngestionBaseUrl,
            /** rtc-gateway-service — LiveKit/WebRTC session transport (port 8195). */
            String rtcGatewayBaseUrl,
            /** Rito — quality, safety & client voice (port 8391). */
            String ritoBaseUrl,
            /** Daidzai — emergency, disaster & public-health response command (port 8392). */
            String daidzaiBaseUrl,
            /** patient-safety-service — pharmacovigilance (ADR/AEFI) SoR + MCAZ workbench (port 8202). */
            String patientSafetyBaseUrl,
            /** identity-assurance-service — identity assurance level + upgrade workflow (port 8201). */
            String identityAssuranceBaseUrl,
            /** Participation — citizen Get-Involved & co-design (port 8393). */
            String participationBaseUrl,
            /** Telemonitoring — monitoring plans, device assignments, readings, alert episodes (port 8394). */
            String telemonitoringBaseUrl
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
            if (mvumoBaseUrl == null) mvumoBaseUrl = "http://localhost:8195";
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
            if (wellnessBaseUrl == null) wellnessBaseUrl = "http://localhost:8125";
            if (workforceGovernanceBaseUrl == null) workforceGovernanceBaseUrl = "http://localhost:8165";
            if (vashandiBaseUrl == null) vashandiBaseUrl = "http://localhost:8167";
            if (khulumaBaseUrl == null) khulumaBaseUrl = "http://localhost:8390";
            if (schedulingServiceBaseUrl == null) schedulingServiceBaseUrl = "http://localhost:8128";
            if (bookingBaseUrl == null) bookingBaseUrl = "http://localhost:8265";
            if (msikaAppsBaseUrl == null) msikaAppsBaseUrl = "http://localhost:8181";
            if (ndilaBaseUrl == null) ndilaBaseUrl = "http://localhost:8155";
            if (nhumeBaseUrl == null) nhumeBaseUrl = "http://localhost:8210";
            if (madiBaseUrl == null) madiBaseUrl = "http://localhost:8300";
            if (liveBaseUrl == null) liveBaseUrl = "http://localhost:8380";
            if (analyticsPipelineBaseUrl == null) analyticsPipelineBaseUrl = "http://localhost:8365";
            if (iotIngestionBaseUrl == null) iotIngestionBaseUrl = "http://localhost:8330";
            if (rtcGatewayBaseUrl == null) rtcGatewayBaseUrl = "http://localhost:8195";
            if (ritoBaseUrl == null) ritoBaseUrl = "http://localhost:8391";
            if (daidzaiBaseUrl == null) daidzaiBaseUrl = "http://localhost:8392";
            if (patientSafetyBaseUrl == null) patientSafetyBaseUrl = "http://localhost:8202";
            if (identityAssuranceBaseUrl == null) identityAssuranceBaseUrl = "http://localhost:8201";
            if (participationBaseUrl == null) participationBaseUrl = "http://localhost:8393";
            if (telemonitoringBaseUrl == null) telemonitoringBaseUrl = "http://localhost:8394";
        }
    }

    /**
     * A closed, privileged port. Nothing can be listening on it, so a call fails immediately with
     * connection-refused instead of reaching a real service.
     */
    public static final String UNREACHABLE_TEST_ENDPOINT = "http://127.0.0.1:1";

    /**
     * Service endpoints for unit tests, every one of them unreachable.
     *
     * <p>This used to pass all nulls, which let the record's compact constructor apply the
     * <em>local development</em> defaults — PCT on 8088, OROS on 8089, inventory-service on 8098.
     * A unit test built on this helper therefore reached the developer's loopback for real
     * whenever a stub missed an overload, and its result depended on what happened to be
     * listening: a service started by hand, a {@code kubectl port-forward}, another project's
     * server. That made assertions about a downstream being unavailable true by luck rather than
     * by construction, and it is one half of why the experience-bff suite failed three different
     * ways on three identical runs (the other half was the Spring integration contexts — see
     * {@code ClosedLoopbackDownstreamsEnvironmentPostProcessor}).</p>
     *
     * <p>Tests that want a downstream to answer should stub it explicitly, with
     * {@code MockRestServiceServer} or WireMock, not by hoping the port is free.</p>
     */
    public static ServiceEndpoints testServiceEndpoints() {
        String u = UNREACHABLE_TEST_ENDPOINT;
        return new ServiceEndpoints(
                u, u, u, u, u, u, u, u, u, u,
                u, u, u, u, u, u, u, u, u, u,
                u, u, u, u, u, u, u, u, u, u,
                u, u, u, u, u, u, u, u, u, u, u,
                u, u, u, u, u, u, u, u, u, u,
                u, u, u, u, u, u, u, u, u, u,
                u, u, u, u, u, u, u, u, u, u,
                u, u, u, u, u, u, u, u
        );
    }

    /**
     * Canonical {@code http://…} hostnames for MockRestServiceServer client tests (pct through notifications).
     * Credential and all remaining slots are {@code null}; the compact constructor applies localhost defaults where needed.
     */
    public static ServiceEndpoints testEndpointsStandardWireMocks() {
        return new ServiceEndpoints(
                "http://pct", "http://oros", "http://pharmacy", "http://butano",
                "http://msika", "http://msika-flow", "http://mushex", "http://vito",
                "http://tuso", "http://varapi", "http://documents", "http://costa",
                "http://coverage", "http://surveillance", "http://campaigns", "http://indawo",
                "http://governance", "http://landela", "http://notifications",
                null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null
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
                // Actor identity is forwarded only-if-absent: a BFF client method that has
                // deliberately pre-set an actor context (e.g. the dependant-registration flow
                // asserting SYSTEM authority to create a guardianship delegation the guardian is
                // authorised to establish) must win over blind inbound forwarding. Every other
                // call leaves these unset, so the caller's actor is forwarded unchanged.
                forwardHeaderIfAbsent(inbound, request, CompanionHeaders.ACTOR_ID);
                forwardHeaderIfAbsent(inbound, request, CompanionHeaders.ACTOR_TYPE);
                forwardHeader(inbound, request, CompanionHeaders.PROVIDER_ID);
                forwardHeader(inbound, request, CompanionHeaders.PURPOSE_OF_USE);
                forwardHeader(inbound, request, CompanionHeaders.DEVICE_FINGERPRINT);
                forwardHeader(inbound, request, CompanionHeaders.ASSURANCE_LEVEL);
                // Authoritatively override X-Assurance-Level with the caller's CURRENT identity-assurance
                // level resolved this request (AssuranceLevelResolutionInterceptor), so a self-service
                // verification upgrade reaches the trust plane and a client cannot forge it (G-CZO-01).
                Object resolvedAssurance = inbound.getAttribute(
                        AssuranceLevelResolutionInterceptor.RESOLVED_ASSURANCE_LEVEL_ATTR);
                if (resolvedAssurance instanceof String level && !level.isBlank()) {
                    request.getHeaders().set(CompanionHeaders.ASSURANCE_LEVEL, level);
                }
                forwardHeader(inbound, request, CompanionHeaders.FACILITY_ID);
                forwardHeader(inbound, request, CompanionHeaders.TUSO_FACILITY_ID);
                forwardHeader(inbound, request, CompanionHeaders.WORKSPACE_ID);
                forwardHeader(inbound, request, CompanionHeaders.DEPARTMENT_ID);
                forwardHeader(inbound, request, CompanionHeaders.WARD_ID);
                forwardHeader(inbound, request, CompanionHeaders.PROGRAMME_ID);
                forwardHeader(inbound, request, CompanionHeaders.SHIFT_ID);
                forwardHeader(inbound, request, CompanionHeaders.WORKFLOW_STATE);
                // Duty proof: the signed WORK_CONTEXT token the PDP introspects to make the
                // operational context above authoritative (validated, not merely trusted).
                forwardHeader(inbound, request, CompanionHeaders.WORK_CONTEXT_TOKEN);
                // Clinical episode correlation. The shell sets X-Trauma-Episode-ID on every
                // resuscitation, ED and blood write (ui/one-ui-shell/src/hooks/queries/useEmergency.ts),
                // and pct/inpatient/madi all read it via @RequestHeader — but until this line existed
                // the BFF silently dropped it, so every resus event reached inpatient-service with no
                // episode id and the cross-service episode timeline was built from nothing. The
                // existing shell-side test only asserted the shell SET the header, which is why the
                // gap survived. Forward, do not synthesize: this header is a correlation id minted
                // upstream by daidzai/PCT, never by the BFF.
                forwardHeader(inbound, request, CompanionHeaders.TRAUMA_EPISODE_ID);
                // Idempotency keys are scoped to ONE mutation: when a BFF handler fans
                // out to several downstream mutations it must set distinct keys, and
                // blind forwarding would clobber them (downstream then 409s the second
                // call as "key reused with different request").
                if (!request.getHeaders().containsKey(CompanionHeaders.IDEMPOTENCY_KEY)) {
                    forwardHeader(inbound, request, CompanionHeaders.IDEMPOTENCY_KEY);
                }
                forwardHeader(inbound, request, CompanionHeaders.CLIENT_TIMEOUT_MS);
                forwardHeader(inbound, request, CompanionHeaders.PATIENT_SHARE_GRANT_ID);
                forwardHeader(inbound, request, CompanionHeaders.VITO_CONTRIBUTION_ID);
                forwardHeader(inbound, request, CompanionHeaders.TEMPORARY_PROVIDER_PUBLIC_ID);
                forwardHeader(inbound, request, CompanionHeaders.PATIENT_SHARE_CORRELATION_ID);
                forwardHeader(inbound, request, CompanionHeaders.EXTERNAL_PROVIDER_TRUST_LEVEL);
                // Forward Health OS Extensibility Doctrine §1 / §14 headers so that
                // service identity, request source, and external-app provenance
                // survive BFF → downstream service hops.
                forwardHeader(inbound, request, X_SERVICE_ID);
                forwardHeader(inbound, request, X_SERVICE_NAME);
                forwardHeader(inbound, request, X_SERVICE_VERSION);
                forwardHeader(inbound, request, X_REQUEST_SOURCE);
                forwardHeader(inbound, request, X_EXTERNAL_APP_ID);
                forwardHeader(inbound, request, X_INTEGRATION_TYPE);
                forwardHeader(inbound, request, X_INTEGRATION_VERSION);
                forwardHeader(inbound, request, X_REQUEST_SIGNATURE);
                forwardHeader(inbound, request, X_AI_SKILL_ID);
                forwardHeader(inbound, request, X_AI_MODEL_REF);
                // Keep forwarding step-up token even when older companion-header artifacts
                // do not expose the constant yet.
                forwardHeader(inbound, request, "X-Step-Up-Token");
            }
            request.getHeaders().set(CompanionHeaders.ACCESS_MODE, "INTERNAL");
            // BFF identifies itself as the caller for downstream S2S trust
            // contracts. Downstream services can use this to authorize against
            // an active S2SContract in integration-hub.
            if (!request.getHeaders().containsKey(X_SERVICE_ID)) {
                request.getHeaders().set(X_SERVICE_ID, "experience-bff");
                request.getHeaders().set(X_SERVICE_NAME, "Experience BFF");
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    public RestTemplate serviceRestTemplate(ClientHttpRequestInterceptor trustHeaderForwardingInterceptor) {
        // JDK HttpClient, NOT SimpleClientHttpRequestFactory. The latter is backed by
        // HttpURLConnection, which cannot send PATCH — it throws ProtocolException, the client
        // catches it, and the BFF returns 502. Seven downstream clients PATCH through this bean
        // (Msika, PatientSafety, Costa, Pacs, Dispatch, and the vashandi swap-decide), so every one
        // of them silently 502'd on any PATCH. Unit tests mock the client and never exercise a real
        // PATCH, so the estate-wide break was invisible until a WireMock loopback test caught it —
        // pin PATCH over a real loopback; mocks hide it.
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
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

    /**
     * Forward an inbound header only if the calling client method has not already set it on the
     * outbound request. Lets a deliberate override (a pre-set actor context) survive the
     * interceptor, mirroring the only-if-absent treatment already applied to idempotency keys.
     */
    private static void forwardHeaderIfAbsent(HttpServletRequest inbound,
                                              org.springframework.http.HttpRequest outbound,
                                              String headerName) {
        if (outbound.getHeaders().containsKey(headerName)) {
            return;
        }
        forwardHeader(inbound, outbound, headerName);
    }
}
