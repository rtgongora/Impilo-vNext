package zw.gov.mohcc.impilo.telemonitoring.integration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.UUID;

/**
 * Trust-header propagation for telemonitoring's READ-ONLY cross-service hops
 * (iot-ingestion device registry, asset-registry equipment) — the msika-flow
 * {@code NhumeClient.copyTrustHeaders} idiom. Downstream controllers run behind
 * {@code RequestContextHolder.require()}, so the v1.1 hard-required set
 * (X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID) must always be present:
 * forwarded from the inbound request where one exists, synthesized for
 * service-originated calls (no servlet request), per the service-to-service v1.1
 * header contract.
 */
final class TrustHeaderPropagation {

    private TrustHeaderPropagation() {
    }

    /** Copy the caller's trust context onto an outbound hop; fall back to service-originated headers. */
    static void apply(HttpServletRequest inbound, HttpHeaders target, UUID tenantId) {
        if (inbound == null) {
            applyServiceOriginated(target, tenantId);
            return;
        }
        copyIfPresent(inbound, target, CompanionHeaders.TENANT_ID);
        copyIfPresent(inbound, target, CompanionHeaders.ACTOR_ID);
        copyIfPresent(inbound, target, CompanionHeaders.ACTOR_TYPE);
        copyIfPresent(inbound, target, CompanionHeaders.PURPOSE_OF_USE);
        copyIfPresent(inbound, target, CompanionHeaders.CORRELATION_ID);
        copyIfPresent(inbound, target, CompanionHeaders.FACILITY_ID);
        copyIfPresent(inbound, target, CompanionHeaders.WORKSPACE_ID);
        copyIfPresent(inbound, target, CompanionHeaders.AUTHORIZATION);
        if (target.getFirst(CompanionHeaders.TENANT_ID) == null && tenantId != null) {
            target.set(CompanionHeaders.TENANT_ID, tenantId.toString());
        }
        if (target.getFirst(CompanionHeaders.CORRELATION_ID) == null) {
            target.set(CompanionHeaders.CORRELATION_ID, UUID.randomUUID().toString());
        }
        String pod = inbound.getHeader(CompanionHeaders.POD_ID);
        target.set(CompanionHeaders.POD_ID, pod != null && !pod.isBlank() ? pod : "national-spine");
        target.set(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
        target.set("x-envoy-internal", "true");
    }

    /** Full v1.1 hard-required set for calls with no servlet request (consumers, sweeps). */
    static void applyServiceOriginated(HttpHeaders target, UUID tenantId) {
        if (tenantId != null) {
            target.set(CompanionHeaders.TENANT_ID, tenantId.toString());
        }
        target.set(CompanionHeaders.POD_ID, "national-spine");
        target.set(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
        target.set(CompanionHeaders.CORRELATION_ID, UUID.randomUUID().toString());
        target.set(CompanionHeaders.ACTOR_ID, "telemonitoring-service");
        target.set(CompanionHeaders.ACTOR_TYPE, "SYSTEM");
        target.set(CompanionHeaders.PURPOSE_OF_USE, "TREATMENT");
        target.set("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound, HttpHeaders target, String name) {
        String value = inbound.getHeader(name);
        if (value != null && !value.isBlank()) {
            target.set(name, value);
        }
    }
}
