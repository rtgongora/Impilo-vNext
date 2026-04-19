package zw.gov.mohcc.impilo.butano.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.ETagSupportEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import ca.uhn.fhir.rest.server.provider.ResourceProviderFactory;
import jakarta.servlet.ServletException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import zw.gov.mohcc.impilo.butano.interceptor.HeaderValidationInterceptor;
import zw.gov.mohcc.impilo.butano.interceptor.PiiPreventionInterceptor;
import zw.gov.mohcc.impilo.butano.interceptor.ProvenanceStampingInterceptor;
import zw.gov.mohcc.impilo.butano.interceptor.TenantEnforcementInterceptor;
import zw.gov.mohcc.impilo.butano.interceptor.TerminologyValidationInterceptor;

import java.util.List;

/**
 * HAPI FHIR RESTful server configuration and servlet registration.
 *
 * <p>This configuration creates a {@link RestfulServer} instance that serves
 * the FHIR R4 API at {@code /fhir/*}. It registers all JPA resource providers
 * (auto-discovered from HAPI's {@link ResourceProviderFactory}) and the five
 * custom BUTANO interceptors that enforce trust-header validation, tenant
 * isolation, PII prevention, provenance stamping, and terminology validation.</p>
 *
 * <p>Interceptor registration order matters:</p>
 * <ol>
 *   <li>{@link HeaderValidationInterceptor} — first, validates and stores trust headers</li>
 *   <li>{@link TenantEnforcementInterceptor} — tenant tag stamping and read isolation</li>
 *   <li>{@link PiiPreventionInterceptor} — rejects PII in Patient resources</li>
 *   <li>{@link ProvenanceStampingInterceptor} — stamps provenance metadata</li>
 *   <li>{@link TerminologyValidationInterceptor} — validates coded elements</li>
 * </ol>
 */
@Configuration
public class FhirRestfulServlet {

    @Value("${hapi.fhir.server-address:http://localhost:8090/fhir}")
    private String serverAddress;

    @Value("${hapi.fhir.default-page-size:20}")
    private int defaultPageSize;

    @Value("${hapi.fhir.max-page-size:200}")
    private int maxPageSize;

    /**
     * Registers the HAPI FHIR RestfulServer as a servlet mapped to /fhir/*.
     *
     * <p>The servlet is created with all JPA resource providers and custom
     * interceptors. It is loaded eagerly (loadOnStartup=1) so that FHIR
     * endpoints are available immediately after application start.</p>
     */
    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServletRegistration(
            ApplicationContext appContext,
            FhirContext fhirContext,
            ResourceProviderFactory resourceProviderFactory,
            HeaderValidationInterceptor headerValidationInterceptor,
            TenantEnforcementInterceptor tenantEnforcementInterceptor,
            PiiPreventionInterceptor piiPreventionInterceptor,
            ProvenanceStampingInterceptor provenanceStampingInterceptor,
            TerminologyValidationInterceptor terminologyValidationInterceptor) {

        RestfulServer server = new RestfulServer(fhirContext) {
            @Override
            protected void initialize() throws ServletException {
                super.initialize();

                // ── Server identity ─────────────────────────────────────
                setServerName("BUTANO — Impilo Shared Health Record");
                setServerVersion("1.0.0");
                setImplementationDescription("National Shared Health Record (PII-free, CPID-only)");

                // ── Server address strategy ─────────────────────────────
                setServerAddressStrategy(new ca.uhn.fhir.rest.server.HardcodedServerAddressStrategy(serverAddress));

                // ── Paging ──────────────────────────────────────────────
                setDefaultPrettyPrint(false);
                setDefaultResponseEncoding(EncodingEnum.JSON);
                setETagSupport(ETagSupportEnum.ENABLED);

                // ── Resource providers (all R4 JPA DAOs) ────────────────
                registerProviders(resourceProviderFactory.createProviders());

                // ── CORS interceptor ────────────────────────────────────
                CorsConfiguration corsConfig = new CorsConfiguration();
                corsConfig.setAllowedOriginPatterns(List.of(
                        "http://localhost:*",
                        "https://*.impilo.health",
                        "https://*.impilo.gov.zw",
                        "capacitor://*",
                        "ionic://*"
                ));
                corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                corsConfig.setAllowedHeaders(List.of(
                        HttpHeaders.ACCEPT,
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.AUTHORIZATION,
                        HttpHeaders.CACHE_CONTROL,
                        "x-tenant-id",
                        "x-correlation-id",
                        "x-actor-id",
                        "x-actor-type",
                        "x-purpose-of-use",
                        "x-facility-id",
                        "x-workspace-id",
                        "x-device-fingerprint",
                        "x-shift-id",
                        "x-decision",
                        "Prefer"
                ));
                corsConfig.setExposedHeaders(List.of(
                        HttpHeaders.LOCATION,
                        HttpHeaders.CONTENT_LOCATION,
                        HttpHeaders.ETAG,
                        "X-Request-Id"
                ));
                corsConfig.setAllowCredentials(true);
                CorsInterceptor corsInterceptor = new CorsInterceptor(corsConfig);
                registerInterceptor(corsInterceptor);

                // ── Response highlighter (HTML browser view) ────────────
                registerInterceptor(new ResponseHighlighterInterceptor());

                // ── BUTANO custom interceptors (order matters) ──────────
                // 1. Header validation first — populates userdata for downstream interceptors
                registerInterceptor(headerValidationInterceptor);
                // 2. Tenant enforcement — stamps and filters by tenant
                registerInterceptor(tenantEnforcementInterceptor);
                // 3. PII prevention — rejects PII in Patient resources
                registerInterceptor(piiPreventionInterceptor);
                // 4. Provenance stamping — stamps full audit metadata
                registerInterceptor(provenanceStampingInterceptor);
                // 5. Terminology validation — validates coded elements against ZIBO
                registerInterceptor(terminologyValidationInterceptor);
            }
        };

        ServletRegistrationBean<RestfulServer> registration =
                new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setLoadOnStartup(1);
        registration.setName("HAPI FHIR Servlet");
        return registration;
    }
}
