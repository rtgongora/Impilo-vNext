package zw.gov.mohcc.impilo.experience.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Security configuration with Keycloak JWT validation and role-based access control.
 *
 * <p>When a JwtDecoder bean is available (production/integration): enforces
 * RS256 signature verification, expiration checking, and path-based RBAC.
 * When no JwtDecoder bean exists and {@code impilo.security.allow-anonymous=true}:
 * permits all requests (dev only). When no JwtDecoder and allow-anonymous is false
 * (default): throws {@link IllegalStateException} to prevent startup with no auth.</p>
 *
 * <h3>Role Groups (aligned with frontend AuthGuardProvider ROLE_GROUPS)</h3>
 * <ul>
 *   <li>ADMIN: SYSTEM_ADMIN, SUPER_ADMIN, FACILITY_ADMIN, DEVELOPER</li>
 *   <li>FINANCE: SYSTEM_ADMIN, FACILITY_ADMIN, FINANCE</li>
 *   <li>CLINICAL: CLINICIAN, NURSE, FACILITY_ADMIN, SYSTEM_ADMIN, DEVELOPER</li>
 *   <li>PRESCRIBER: CLINICIAN, FACILITY_ADMIN, SYSTEM_ADMIN, DEVELOPER</li>
 *   <li>DISPENSER: PHARMACIST, FACILITY_ADMIN, SYSTEM_ADMIN, DEVELOPER</li>
 *   <li>QUEUE: CLINICIAN, NURSE, SUPPORT_AGENT, FACILITY_ADMIN, SYSTEM_ADMIN, DEVELOPER</li>
 *   <li>COMMERCE: FINANCE, CLINICIAN, NURSE, PHARMACIST, SUPPORT_AGENT, FACILITY_ADMIN, SYSTEM_ADMIN, DEVELOPER</li>
 *   <li>CITIZEN: CITIZEN, SYSTEM_ADMIN, DEVELOPER</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    // ── Role group arrays ────────────────────────────────────────────
    // Each array lists the Keycloak realm roles that grant access to the group.
    // SYSTEM_ADMIN, SUPER_ADMIN, and DEVELOPER have override access to all groups.

    private static final String[] PLATFORM_OVERRIDES = {
            "SYSTEM_ADMIN", "DEVELOPER", "SUPER_ADMIN"};

    private static final String[] ADMIN_ROLES = mergeRoleSets(
            new String[]{"FACILITY_ADMIN"}, PLATFORM_OVERRIDES);

    /** Tuso locality proposal approval + ZW geo bulk import (registry operators). */
    private static final String[] REGISTRY_OPS_ROLES = mergeRoleSets(
            new String[]{"REGISTRY_ADMIN"}, PLATFORM_OVERRIDES);

    private static final String[] FINANCE_ROLES = mergeRoleSets(
            new String[]{"FACILITY_ADMIN", "FINANCE"}, new String[]{"SYSTEM_ADMIN", "SUPER_ADMIN"});

    private static final String[] PAYER_OPS_ROLES = mergeRoleSets(
            new String[]{"FINANCE"}, PLATFORM_OVERRIDES);

    private static final String[] MSIKA_GOVERNANCE_ROLES = mergeRoleSets(
            new String[]{"FACILITY_ADMIN", "FINANCE"}, PLATFORM_OVERRIDES);

    private static final String[] CLINICAL_ROLES = mergeRoleSets(
            new String[]{"CLINICIAN", "NURSE", "FACILITY_ADMIN"}, PLATFORM_OVERRIDES);

    private static final String[] PRESCRIBER_ROLES = mergeRoleSets(
            new String[]{"CLINICIAN", "FACILITY_ADMIN"}, PLATFORM_OVERRIDES);

    private static final String[] DISPENSER_ROLES = mergeRoleSets(
            new String[]{"PHARMACIST", "FACILITY_ADMIN"}, PLATFORM_OVERRIDES);

    private static final String[] QUEUE_ROLES = mergeRoleSets(
            new String[]{"CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN"}, PLATFORM_OVERRIDES);

    private static final String[] COMMERCE_ROLES = mergeRoleSets(
            new String[]{"FINANCE", "CLINICIAN", "NURSE", "PHARMACIST", "SUPPORT_AGENT", "FACILITY_ADMIN"},
            PLATFORM_OVERRIDES);

    private static final String[] CITIZEN_ROLES = mergeRoleSets(
            new String[]{"CITIZEN"}, PLATFORM_OVERRIDES);

    // Health OS §4: Caregiving roles — delegated care partners and family caregivers
    private static final String[] CAREGIVER_ROLES = mergeRoleSets(
            new String[]{"CAREGIVER", "CARE_PARTNER", "CITIZEN"}, new String[]{"SYSTEM_ADMIN", "SUPER_ADMIN"});

    // Health OS §7: Public health and community health roles (+ DEVELOPER for governed dev access)
    private static final String[] PUBLIC_HEALTH_ROLES = mergeRoleSets(
            new String[]{"PUBLIC_HEALTH_OFFICER", "ENV_HEALTH", "CHW", "FACILITY_ADMIN"}, PLATFORM_OVERRIDES);

    /** District / provincial / national operational snapshots (queue stats across facilities). */
    private static final String[] OPERATIONS_AGGREGATE_ROLES = mergeRoleSets(
            mergeRoleSets(mergeRoleSets(CLINICAL_ROLES, QUEUE_ROLES), PUBLIC_HEALTH_ROLES),
            new String[]{"HIE_ADMIN"});

    @Autowired(required = false)
    private JwtDecoder jwtDecoder;

    @Autowired
    private org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (jwtDecoder != null) {
            log.info("JWT validation ENABLED — enforcing role-based access control");
            http
                .authorizeHttpRequests(auth -> auth
                    // ── CORS preflight ───────────────────────────────────
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // ── Public endpoints ──────────────────────────────────
                    .requestMatchers("/internal/v1/auth/**").permitAll()
                    // Zero-to-one bootstrap: no national admin exists yet — policy enforced in BootstrapService/OPA
                    .requestMatchers(HttpMethod.GET, "/internal/v1/bootstrap/status").permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/v1/bootstrap/validate-token").permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/v1/bootstrap/first-admin").permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/v1/bootstrap/activate").permitAll()
                    .requestMatchers("/internal/v1/bootstrap/recovery/**").hasAnyRole(PLATFORM_OVERRIDES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/bootstrap/close").hasAnyRole(PLATFORM_OVERRIDES)
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers("/health/version").permitAll()
                    .requestMatchers(HttpMethod.GET, "/internal/v1/profile/visibility").authenticated()

                    // ── Admin zone ────────────────────────────────────────
                    .requestMatchers("/internal/v1/admin/**").hasAnyRole(ADMIN_ROLES)

                    // ── Finance zone ──────────────────────────────────────
                    // Patient self-service financials (COSTA-backed); any authenticated user — ABAC should bind cpid to actor.
                    .requestMatchers("/internal/v1/finance/patient-accounts/**").authenticated()
                    .requestMatchers("/internal/v1/finance/payment-plans/**").authenticated()
                    .requestMatchers("/internal/v1/finance/documents/**").authenticated()
                    .requestMatchers("/internal/v1/finance/payer-claims/**").hasAnyRole(PAYER_OPS_ROLES)
                    .requestMatchers("/internal/v1/finance/payer-ops/**").hasAnyRole(PAYER_OPS_ROLES)
                    .requestMatchers("/internal/v1/finance/reconciliation/**").hasAnyRole(PAYER_OPS_ROLES)
                    .requestMatchers("/internal/v1/msika/**").hasAnyRole(MSIKA_GOVERNANCE_ROLES)
                    .requestMatchers("/internal/v1/finance/**").hasAnyRole(FINANCE_ROLES)
                    .requestMatchers("/internal/v1/product-registry/**").hasAnyRole(COMMERCE_ROLES)
                    .requestMatchers("/internal/v1/commerce/**").hasAnyRole(COMMERCE_ROLES)

                    // ── Multi-facility operational snapshots (oversight roles) ──
                    .requestMatchers(HttpMethod.POST, "/internal/v1/operations/facility-queue-snapshots")
                            .hasAnyRole(OPERATIONS_AGGREGATE_ROLES)
                    .requestMatchers(HttpMethod.GET, "/internal/v1/operations/national-kpis")
                            .hasAnyRole(OPERATIONS_AGGREGATE_ROLES)

                    // ── Queue management ──────────────────────────────────
                    .requestMatchers(HttpMethod.POST, "/internal/v1/queue/**")
                            .hasAnyRole(QUEUE_ROLES)

                    // ── Pharmacy: prescriptions require prescriber role ───
                    .requestMatchers(HttpMethod.POST, "/internal/v1/pharmacy/prescriptions")
                            .hasAnyRole(PRESCRIBER_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/pharmacy/prescriptions/*/cancel")
                            .hasAnyRole(PRESCRIBER_ROLES)
                    // ── Pharmacy: dispense requires dispenser role ────────
                    .requestMatchers(HttpMethod.POST, "/internal/v1/pharmacy/dispense")
                            .hasAnyRole(DISPENSER_ROLES)
                    // ── Pharmacy: sovereign pharmacy-service proxies ───────
                    .requestMatchers(HttpMethod.GET, "/internal/v1/pharmacy/upstream/dispense-orders/patient/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.GET, "/internal/v1/pharmacy/upstream/worklists")
                            .hasAnyRole(DISPENSER_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/pharmacy/upstream/dispense-orders/*/complete")
                            .hasAnyRole(DISPENSER_ROLES)

                    // ── Mobile provider: prescriptions → prescriber ───────
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/prescriptions")
                            .hasAnyRole(PRESCRIBER_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/prescriptions/*/cancel")
                            .hasAnyRole(PRESCRIBER_ROLES)

                    // ── Workspace selection requires clinical staff ──────
                    .requestMatchers(HttpMethod.GET, "/internal/v1/workspaces")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/workspaces/*/activate")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── Clinical write endpoints (broad clinical staff) ───
                    .requestMatchers(HttpMethod.POST, "/internal/v1/encounters/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/triage/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/vitals/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/growth/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.GET, "/internal/v1/maternity/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/maternity/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.PATCH, "/internal/v1/maternity/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/labour-monitoring/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/clinical-notes/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.GET, "/internal/v1/lab-worklists")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/lab-worklists/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.GET, "/internal/v1/lab-catalog")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.GET, "/internal/v1/lab-reconciliation")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/lab-reconciliation/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/lab-orders/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/conditions/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/allergies/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.DELETE, "/internal/v1/allergies/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/immunizations/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/referrals/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/clinical-documents/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/appointments/**")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── MADI — blood donation, blood bank, transfusion ───
                    .requestMatchers("/internal/v1/madi/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers("/internal/v1/mobile/citizen/madi/**")
                            .hasAnyRole(CITIZEN_ROLES)
                    .requestMatchers("/internal/v1/mobile/provider/madi/**")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── Impilo Live — events, webinars, broadcasts ─────
                    .requestMatchers("/internal/v1/live/**")
                            .authenticated()
                    .requestMatchers("/internal/v1/mobile/citizen/live/**")
                            .hasAnyRole(CITIZEN_ROLES)
                    .requestMatchers("/internal/v1/mobile/provider/live/**")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── PACS / DICOM viewer ──────────────────────────────
                    .requestMatchers("/internal/v1/pacs/**")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── Governed imaging (PACS adapter via BFF) ───────────
                    .requestMatchers("/internal/v1/imaging/**")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── Mobile provider clinical operations ───────────────
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/encounters/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/triage/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/vitals/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.DELETE, "/internal/v1/mobile/provider/vitals/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/discharge")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── Health Connect–style wellness ingest (typed changesets, deduped)
                    .requestMatchers("/internal/v1/wellness/connect/**")
                            .authenticated()

                    // ── Citizen wellness (web + mobile): any authenticated user may manage own rows;
                    //     patientId must match actor in a future ABAC pass. Placed before stricter citizen/**.
                    .requestMatchers("/internal/v1/mobile/citizen/wellness/**")
                            .authenticated()

                    // ── Citizen monitoring devices (shell + mobile): same trust bar as wellness until ABAC binds patientId.
                    .requestMatchers("/internal/v1/mobile/citizen/monitoring/**")
                            .authenticated()

                    // ── Citizen mobile endpoints ──────────────────────────
                    .requestMatchers("/internal/v1/mobile/citizen/**")
                            .hasAnyRole(CITIZEN_ROLES)

                    // ── Caregiving endpoints (Health OS §4) ──────────────
                    .requestMatchers("/internal/v1/caregiving/**")
                            .hasAnyRole(CAREGIVER_ROLES)

                    // ── Public health endpoints (Health OS §7) ────────────
                    .requestMatchers("/internal/v1/public-health/**")
                            .hasAnyRole(PUBLIC_HEALTH_ROLES)

                    // ── Device registry endpoints (Health OS §17) ─────────
                    .requestMatchers("/internal/v1/devices/**")
                            .authenticated()

                    // ── Guidance/knowledge endpoints (Health OS §2a) ──────
                    .requestMatchers("/internal/v1/guidance/**")
                            .authenticated()
                    // ── Integration hub (national routing / dispatch) ───────
                    .requestMatchers("/internal/v1/integration-hub/**")
                            .hasAnyRole(ADMIN_ROLES)
                    // ── Registry downstream / shell health (ops / integration validation) ───
                    .requestMatchers("/internal/v1/registry-downstream/**", "/internal/v1/registry-shell/**")
                            .hasAnyRole(ADMIN_ROLES)
                    // ── Clinical knowledge curation (admin-equivalent) ─────────
                    .requestMatchers("/internal/v1/clinical/curation/**")
                            .hasAnyRole(ADMIN_ROLES)
                    // ── Clinical source PDF ingestion (operators / admin only) ──
                    .requestMatchers("/internal/v1/clinical/source/**")
                            .hasAnyRole(ADMIN_ROLES)
                    // ── National clinical knowledge (EDLIZ-aligned) ───────
                    .requestMatchers("/internal/v1/clinical/**")
                            .authenticated()
                    .requestMatchers("/internal/v1/search/**")
                            .authenticated()
                    .requestMatchers("/internal/v1/learning/**")
                            .authenticated()
                    .requestMatchers("/internal/v1/intelligence-plane/**")
                            .authenticated()

                    // ── Emergency care-first (Health OS §12) ─────────────
                    .requestMatchers("/internal/v1/emergency/**")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── Consent pipeline (Privacy Policy / Terms) ────────
                    // Self-service consent: any authenticated user
                    .requestMatchers("/internal/v1/consent/accept").authenticated()
                    .requestMatchers("/internal/v1/consent/status").authenticated()
                    .requestMatchers("/internal/v1/consent/history").authenticated()
                    .requestMatchers("/internal/v1/consent/revoke").authenticated()
                    // Operator-assisted consent: requires clinical or admin role
                    .requestMatchers("/internal/v1/consent/operator").hasAnyRole(CLINICAL_ROLES)
                    // SMS/USSD inbound (system-to-system from gateway): admin/system only
                    .requestMatchers("/internal/v1/consent/sms").hasAnyRole(ADMIN_ROLES)
                    .requestMatchers("/internal/v1/consent/ussd").hasAnyRole(ADMIN_ROLES)
                    .requestMatchers("/internal/v1/consent/send-request").hasAnyRole(ADMIN_ROLES)

                    // ── Account deletion (Privacy Policy §13) ───────────
                    .requestMatchers("/internal/v1/account/delete").authenticated()
                    .requestMatchers("/internal/v1/account/delete/status").authenticated()
                    .requestMatchers("/internal/v1/account/delete/cancel").authenticated()

                    // ── Privacy & display preferences ───────────────────
                    .requestMatchers("/internal/v1/settings/privacy/**").authenticated()
                    .requestMatchers("/internal/v1/settings/display/**").authenticated()

                    // ── Patient-mediated external provider collaboration (VITO-backed) ──
                    .requestMatchers("/internal/v1/public/patient-shares/**").permitAll()
                    .requestMatchers("/internal/v1/citizen/clients/*/patient-shares/**")
                            .hasAnyRole(CITIZEN_ROLES)

                    // ── Registry ZW geography bulk import + locality approval (ops) ──
                    .requestMatchers(HttpMethod.POST, "/internal/v1/registry/geo/zw/bulk")
                            .hasAnyRole(REGISTRY_OPS_ROLES)
                    .requestMatchers(HttpMethod.POST,
                            "/internal/v1/registry/localities/proposals/*/approve",
                            "/internal/v1/registry/localities/proposals/*/reject")
                            .hasAnyRole(REGISTRY_OPS_ROLES)
                    .requestMatchers("/internal/v1/registry/zibo/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers("/internal/v1/registry/geo/**",
                            "/internal/v1/registry/localities/**",
                            "/internal/v1/registry/coverage/preview")
                            .hasAnyRole(mergeRoleSets(CLINICAL_ROLES, QUEUE_ROLES))
                    .requestMatchers(HttpMethod.POST,
                            "/internal/v1/registry/provider-council/obligations/*/mushex-intent",
                            "/internal/v1/registry/provider-council/obligations/*/sync-payment")
                            .hasAnyRole(mergeRoleSets(CLINICAL_ROLES, REGISTRY_OPS_ROLES))

                    // ── VITO client registry (staff / admin) ──────────────
                    .requestMatchers("/internal/v1/vito/client-registry/**").authenticated()

                    // ── All other endpoints — authenticated ───────────────
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter()))
                );
        } else if (allowAnonymous) {
            log.warn("SECURITY: JWT validation DISABLED — impilo.security.allow-anonymous=true. "
                    + "All endpoints are open. This MUST NOT be used in production.");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            throw new IllegalStateException(
                    "No JwtDecoder bean found and impilo.security.allow-anonymous is false. "
                    + "Configure OAuth2 resource server or set impilo.security.allow-anonymous=true for dev.");
        }

        return http.build();
    }

    /** Spring {@code hasAnyRole} is {@code String...}; pass a single merged array for union role sets. */
    private static String[] mergeRoleSets(String[] first, String[] second) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (String r : first) {
            roles.add(r);
        }
        for (String r : second) {
            roles.add(r);
        }
        return roles.toArray(String[]::new);
    }

    @Bean
    public JwtAuthenticationConverter keycloakJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    static class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<GrantedAuthority> authorities = new ArrayList<>();

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List<?> roles) {
                    for (Object role : roles) {
                        String r = role.toString();
                        if (!r.startsWith("default-roles-")
                                && !r.equals("offline_access")
                                && !r.equals("uma_authorization")) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
                        }
                    }
                }
            }

            return authorities;
        }
    }
}
