package zw.gov.mohcc.impilo.butano.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HAPI FHIR interceptor that validates mandatory TSHEPO trust headers on every
 * inbound FHIR request.
 *
 * <p>This is the first interceptor in the chain and serves two purposes:</p>
 * <ol>
 *   <li>Validates that all mandatory trust headers are present. If any are missing
 *       and the request is not a break-glass override, the request is rejected with
 *       HTTP 403 Forbidden.</li>
 *   <li>Stores all trust header values in {@link RequestDetails#getUserData()} so
 *       downstream interceptors (tenant enforcement, provenance stamping, etc.) can
 *       access them without re-parsing the HTTP request.</li>
 * </ol>
 *
 * <p>Mandatory headers (must be present on every FHIR request):</p>
 * <ul>
 *   <li>{@code X-Tenant-Id} — the tenant UUID for data isolation</li>
 *   <li>{@code X-Correlation-Id} — request correlation for distributed tracing</li>
 *   <li>{@code X-Actor-Id} — the authenticated actor (user/service) identifier</li>
 *   <li>{@code X-Actor-Type} — the actor type (PRACTITIONER, SYSTEM, etc.)</li>
 *   <li>{@code X-Purpose-Of-Use} — FHIR purpose of use code (TREAT, HPAYMT, etc.)</li>
 * </ul>
 *
 * <p>Break-glass override: if the {@code X-Decision} header contains "BREAK_GLASS",
 * header validation is relaxed (missing optional headers are tolerated). However,
 * the break-glass request is still recorded and flagged for audit.</p>
 *
 * <p>User data keys stored for downstream interceptors:</p>
 * <ul>
 *   <li>{@code TRUST_TENANT_ID}, {@code TRUST_CORRELATION_ID}, {@code TRUST_ACTOR_ID},
 *       {@code TRUST_ACTOR_TYPE}, {@code TRUST_PURPOSE_OF_USE}, {@code TRUST_FACILITY_ID},
 *       {@code TRUST_WORKSPACE_ID}, {@code TRUST_DEVICE_FINGERPRINT}, {@code TRUST_SHIFT_ID},
 *       {@code TRUST_DECISION}, {@code TRUST_BREAK_GLASS}</li>
 * </ul>
 */
@Interceptor
@Component
public class HeaderValidationInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HeaderValidationInterceptor.class);

    // ── Header names ────────────────────────────────────────────────────
    public static final String H_TENANT_ID          = "X-Tenant-Id";
    public static final String H_CORRELATION_ID     = "X-Correlation-Id";
    public static final String H_ACTOR_ID           = "X-Actor-Id";
    public static final String H_ACTOR_TYPE         = "X-Actor-Type";
    public static final String H_PURPOSE_OF_USE     = "X-Purpose-Of-Use";
    public static final String H_FACILITY_ID        = "X-Facility-Id";
    public static final String H_WORKSPACE_ID       = "X-Workspace-Id";
    public static final String H_DEVICE_FINGERPRINT = "X-Device-Fingerprint";
    public static final String H_SHIFT_ID           = "X-Shift-Id";
    public static final String H_DECISION           = "X-Decision";

    // ── User data keys (used by downstream interceptors) ────────────────
    public static final String UD_TENANT_ID          = "TRUST_TENANT_ID";
    public static final String UD_CORRELATION_ID     = "TRUST_CORRELATION_ID";
    public static final String UD_ACTOR_ID           = "TRUST_ACTOR_ID";
    public static final String UD_ACTOR_TYPE         = "TRUST_ACTOR_TYPE";
    public static final String UD_PURPOSE_OF_USE     = "TRUST_PURPOSE_OF_USE";
    public static final String UD_FACILITY_ID        = "TRUST_FACILITY_ID";
    public static final String UD_WORKSPACE_ID       = "TRUST_WORKSPACE_ID";
    public static final String UD_DEVICE_FINGERPRINT = "TRUST_DEVICE_FINGERPRINT";
    public static final String UD_SHIFT_ID           = "TRUST_SHIFT_ID";
    public static final String UD_DECISION           = "TRUST_DECISION";
    public static final String UD_BREAK_GLASS        = "TRUST_BREAK_GLASS";

    // Mandatory headers that must be present on every request
    private static final String[] MANDATORY_HEADERS = {
            H_TENANT_ID,
            H_CORRELATION_ID,
            H_ACTOR_ID,
            H_ACTOR_TYPE,
            H_PURPOSE_OF_USE
    };

    private static final String BREAK_GLASS_VALUE = "BREAK_GLASS";

    /**
     * Validates trust headers on every incoming FHIR request before it is handled.
     *
     * <p>Extracts all trust headers from the HTTP request, checks for mandatory
     * header presence, and stores values in RequestDetails user data for downstream
     * interceptors.</p>
     *
     * @throws ForbiddenOperationException if mandatory headers are missing and
     *                                      break-glass is not active
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
    public void validateHeaders(RequestDetails requestDetails) {
        HttpServletRequest httpRequest = null;
        if (requestDetails instanceof ServletRequestDetails servletRequestDetails) {
            httpRequest = servletRequestDetails.getServletRequest();
        }

        if (httpRequest == null) {
            log.warn("Non-servlet request received — cannot extract trust headers. Rejecting.");
            throw new ForbiddenOperationException(
                    "BUTANO requires trust headers on all FHIR requests. Non-HTTP request rejected.");
        }

        // Extract all headers
        String tenantId = httpRequest.getHeader(H_TENANT_ID);
        String correlationId = httpRequest.getHeader(H_CORRELATION_ID);
        String actorId = httpRequest.getHeader(H_ACTOR_ID);
        String actorType = httpRequest.getHeader(H_ACTOR_TYPE);
        String purposeOfUse = httpRequest.getHeader(H_PURPOSE_OF_USE);
        String facilityId = httpRequest.getHeader(H_FACILITY_ID);
        String workspaceId = httpRequest.getHeader(H_WORKSPACE_ID);
        String deviceFingerprint = httpRequest.getHeader(H_DEVICE_FINGERPRINT);
        String shiftId = httpRequest.getHeader(H_SHIFT_ID);
        String decision = httpRequest.getHeader(H_DECISION);

        // Determine break-glass status
        boolean breakGlass = decision != null && decision.contains(BREAK_GLASS_VALUE);

        // Validate mandatory headers (unless break-glass)
        if (!breakGlass) {
            Map<String, String> mandatoryValues = new LinkedHashMap<>();
            mandatoryValues.put(H_TENANT_ID, tenantId);
            mandatoryValues.put(H_CORRELATION_ID, correlationId);
            mandatoryValues.put(H_ACTOR_ID, actorId);
            mandatoryValues.put(H_ACTOR_TYPE, actorType);
            mandatoryValues.put(H_PURPOSE_OF_USE, purposeOfUse);

            StringBuilder missing = new StringBuilder();
            for (Map.Entry<String, String> entry : mandatoryValues.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    if (!missing.isEmpty()) {
                        missing.append(", ");
                    }
                    missing.append(entry.getKey());
                }
            }

            if (!missing.isEmpty()) {
                log.warn("FHIR request rejected — missing mandatory trust headers: {}", missing);
                throw new ForbiddenOperationException(
                        "Missing mandatory trust headers: " + missing +
                        ". All FHIR requests must include TSHEPO trust headers. " +
                        "Ensure the request passes through the Envoy/TSHEPO gateway.");
            }
        } else {
            log.warn("BREAK_GLASS request from actor={} tenant={} correlation={} — " +
                      "relaxed header validation",
                    actorId, tenantId, correlationId);
        }

        // Store all header values in request user data for downstream interceptors
        requestDetails.getUserData().put(UD_TENANT_ID, tenantId);
        requestDetails.getUserData().put(UD_CORRELATION_ID, correlationId);
        requestDetails.getUserData().put(UD_ACTOR_ID, actorId);
        requestDetails.getUserData().put(UD_ACTOR_TYPE, actorType);
        requestDetails.getUserData().put(UD_PURPOSE_OF_USE, purposeOfUse);
        requestDetails.getUserData().put(UD_FACILITY_ID, facilityId);
        requestDetails.getUserData().put(UD_WORKSPACE_ID, workspaceId);
        requestDetails.getUserData().put(UD_DEVICE_FINGERPRINT, deviceFingerprint);
        requestDetails.getUserData().put(UD_SHIFT_ID, shiftId);
        requestDetails.getUserData().put(UD_DECISION, decision);
        requestDetails.getUserData().put(UD_BREAK_GLASS, breakGlass);

        log.debug("Trust headers validated — tenant={}, actor={}, correlation={}, breakGlass={}",
                tenantId, actorId, correlationId, breakGlass);
    }
}
