package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;
import zw.gov.mohcc.impilo.experience.workcontext.WorkContextResolutionService;
import zw.gov.mohcc.impilo.experience.workcontext.WorkContextSourceStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Surfaces the C2 Vashandi work-context read-model to the shell so the
 * WHERE/WHAT context picker can render active assignments (facility / department /
 * ward / service-point / virtual-pool / above-site × role/workspace), the
 * current check-in state, and whether a context chooser is required.
 *
 * Composition only — the BFF persists nothing. Vashandi is the SoR. Reached
 * behind the Envoy ext_authz → TSHEPO gate (CONTEXT-SELECT specced to track P).
 * Degrades honestly: if upstream is unavailable, returns an empty/unresolved
 * context with {@code integrationStatus=UPSTREAM_UNAVAILABLE} rather than a 5xx.
 */
@RestController
@RequestMapping("/internal/v1/work-context")
public class WorkContextController {

    private static final Logger log = LoggerFactory.getLogger(WorkContextController.class);

    private final VashandiServiceClient vashandiClient;
    private final zw.gov.mohcc.impilo.experience.client.VarapiServiceClient varapiClient;
    private final zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient tshepoIdentityClient;
    private final zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient orgRegistryClient;
    private final WorkContextResolutionService resolutionService;

    public WorkContextController(VashandiServiceClient vashandiClient,
                                 zw.gov.mohcc.impilo.experience.client.VarapiServiceClient varapiClient,
                                 zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient tshepoIdentityClient,
                                 zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient orgRegistryClient,
                                 WorkContextResolutionService resolutionService) {
        this.vashandiClient = vashandiClient;
        this.varapiClient = varapiClient;
        this.tshepoIdentityClient = tshepoIdentityClient;
        this.orgRegistryClient = orgRegistryClient;
        this.resolutionService = resolutionService;
    }

    /**
     * Typed lane for the context chooser (Phase C): unions all six work-context
     * families (facility clinical, department/facility/jurisdiction/programme
     * management, virtual, technical/facility support, regulatory) into one
     * ranked, deduplicated list, instead of the caller having to separately
     * poll the facility-clinical-only {@code GET /work-context} above.
     */
    @GetMapping("/resolved")
    public ResponseEntity<Map<String, Object>> getResolvedWorkContexts(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String entryRoute) {

        WorkContextResolutionService.ResolutionOutcome outcome = resolutionService.resolve(actorId, entryRoute);

        List<Map<String, Object>> contexts = new ArrayList<>();
        for (ResolvedWorkContext c : outcome.contexts()) {
            contexts.add(toContextView(c));
        }
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (WorkContextSourceStatus s : outcome.sourceStatuses()) {
            Map<String, Object> sv = new LinkedHashMap<>();
            sv.put("system", s.system());
            sv.put("state", s.state().name());
            if (s.message() != null) sv.put("message", s.message());
            statuses.add(sv);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("contexts", contexts);
        attributes.put("sourceStatuses", statuses);
        attributes.put("recommendedContextId", outcome.recommendedContextId());
        attributes.put("requiresContextChooser", outcome.requiresContextChooser());
        attributes.put("friendlyResolutionState", outcome.friendlyResolutionState() != null ? outcome.friendlyResolutionState() : "");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", actorId, "type", "work-context-resolved", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static Map<String, Object> toContextView(ResolvedWorkContext c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("contextId", c.contextId());
        v.put("contextKind", c.contextKind());
        v.put("sourceSystem", c.sourceSystem());
        v.put("facilityId", c.facilityId());
        v.put("organisationId", c.organisationId());
        v.put("jurisdictionCode", c.jurisdictionCode());
        v.put("programmeId", c.programmeId());
        v.put("departmentId", c.departmentId());
        v.put("roleTemplateId", c.roleTemplateId());
        v.put("availableModes", c.availableModes());
        v.put("defaultMode", c.defaultMode());
        v.put("modeSource", c.modeSource());
        v.put("restrictions", c.restrictions());
        v.put("label", c.label());
        v.put("groupHint", c.groupHint());
        v.put("rankScore", c.rankScore());
        return v;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getWorkContext(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {

        Map<String, Object> attributes;
        String integrationStatus;
        JsonNode upstream = vashandiClient.fetchWorkContext(actorId);
        if (upstream == null || upstream.isNull()) {
            log.debug("work-context: vashandi upstream unavailable for actor request {}", requestId);
            attributes = unresolved();
            integrationStatus = "UPSTREAM_UNAVAILABLE";
        } else {
            attributes = toAttributes(upstream);
            integrationStatus = Boolean.TRUE.equals(attributes.get("resolved")) ? "LIVE" : "NO_WORK_CONTEXT";
        }
        attributes.put("integrationStatus", integrationStatus);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", actorId,
                "type", "work-context",
                "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Start or switch a provider work session (D-P3 / PJ5): prove the requested
     * facility (and workspace, when given) against the actor's ACTIVE Vashandi
     * assignments, then mint a duty-scoped WORK_CONTEXT token. A switch passes
     * {@code previousJti}; the old token is revoked before reissue. The proof
     * happens HERE — tshepo-identity only binds what this composition proved.
     */
    @org.springframework.web.bind.annotation.PostMapping("/session")
    public ResponseEntity<Map<String, Object>> startWorkSession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {

        String facilityId = str(body.get("facilityId"));
        String workspaceId = str(body.get("workspaceId"));
        String previousJti = str(body.get("previousJti"));
        String regulatoryOrgId = str(body.get("organisationId"));

        // ROM-W2 — regulatory (org-scoped) work session. Regulatory personnel are NOT
        // facility-attached and are not varapi providers; their context is an ACTIVE
        // org-registry appointment. This branch bypasses the provider + facility gates.
        if (facilityId == null && regulatoryOrgId != null) {
            return startRegulatorySession(tenantId, requestId, correlationId, actorId,
                    regulatoryOrgId, previousJti);
        }
        if (facilityId == null) {
            return errorBody(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "CONTEXT_REQUIRED", "A facility (clinical) or organisation (regulatory) is required to start a work session.",
                    requestId, correlationId);
        }

        // 1) The person must be a linked, operational provider.
        JsonNode provider = varapiClient.getProviderByHealthId(actorId);
        String providerPublicId = provider != null && !provider.isNull()
                ? text(provider, "providerPublicId") : null;
        if (providerPublicId == null) {
            return errorBody(org.springframework.http.HttpStatus.FORBIDDEN,
                    "WORK_SESSION_UNAVAILABLE",
                    "Professional Work access is currently unavailable. Open My Professional to view the status.",
                    requestId, correlationId);
        }

        // 2) The requested context must be one of the actor's ACTIVE assignments.
        JsonNode workContext = vashandiClient.fetchWorkContext(actorId);
        JsonNode matched = matchAssignment(workContext, facilityId, workspaceId);
        if (matched == null) {
            // Generic denial — never disclose which contexts DO exist beyond the
            // picker the actor already sees via GET /work-context.
            return errorBody(org.springframework.http.HttpStatus.FORBIDDEN,
                    "WORK_SESSION_UNAVAILABLE",
                    "Professional Work access is currently unavailable for the selected workplace.",
                    requestId, correlationId);
        }

        // 3) Bind the PROVEN context into a short-lived work token.
        Map<String, Object> issueRequest = new LinkedHashMap<>();
        issueRequest.put("tenantId", tenantId);
        issueRequest.put("actorId", actorId);
        issueRequest.put("providerPublicId", providerPublicId);
        issueRequest.put("facilityId", facilityId);
        if (workspaceId != null) {
            issueRequest.put("workspaceId", workspaceId);
        }
        String departmentId = text(matched, "departmentId");
        if (departmentId != null) {
            issueRequest.put("departmentId", departmentId);
        }
        // Full operational context from the matched ACTIVE assignment, so the signed
        // token — not the loose picker headers — becomes the PDP's authoritative source
        // for the ward / programme / organisation / department dimensions.
        String unitId = text(matched, "unitId");
        if (unitId != null) {
            issueRequest.put("wardId", unitId);
        }
        String programmeId = text(matched, "programmeId");
        if (programmeId != null) {
            issueRequest.put("programmeId", programmeId);
        }
        String organisationId = text(matched, "organisationId");
        if (organisationId != null) {
            issueRequest.put("organisationId", organisationId);
        }
        String assignmentId = text(matched, "assignmentId");
        if (assignmentId != null) {
            issueRequest.put("assignmentId", assignmentId);
        }
        String roleTemplateId = text(matched, "roleTemplateId");
        if (roleTemplateId != null) {
            issueRequest.put("roleTemplateId", roleTemplateId);
        }
        // TODO(Phase C): mode is hardcoded pending the resolver's proveContext
        // restructuring (POST /work-context/session/mode-aware) — CLINICAL_CARE
        // is correct for every context this branch proves today (a Vashandi
        // clinical facility assignment), but the request should carry the
        // caller's requested mode once non-clinical facility contexts
        // (department/facility management) are resolvable here.
        issueRequest.put("workMode", "CLINICAL_CARE");
        issueRequest.put("purposeOfUse", "TREATMENT");
        if (previousJti != null) {
            issueRequest.put("previousJti", previousJti);
        }

        JsonNode issued = tshepoIdentityClient.issueWorkContextToken(issueRequest);
        JsonNode data = issued != null ? issued.path("data") : null;
        if (data == null || data.isMissingNode() || data.isNull()) {
            return errorBody(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "WORK_TOKEN_UNAVAILABLE",
                    "The work session could not be started. Try again.",
                    requestId, correlationId);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("token", text(data, "token"));
        attributes.put("jti", text(data, "jti"));
        attributes.put("expiresAt", text(data, "expiresAt"));
        attributes.put("facilityId", facilityId);
        attributes.put("workspaceId", workspaceId);
        attributes.put("roleTemplateId", roleTemplateId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", actorId, "type", "work-session", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Switch mode WITHIN an already-resolved work context (Phase C, first use of
     * {@link WorkContextResolutionService#proveContext}) — e.g. a ward charge
     * nurse moving CLINICAL_CARE → DEPARTMENT_MANAGEMENT at the same facility.
     * A mode change is never a token mutation, for the same reason a context
     * switch never is: the mode IS the access envelope. Always mints a fresh
     * token and revokes {@code previousJti}.
     *
     * <p>{@code contextId} is a value the caller must already hold from
     * {@code GET /work-context/resolved} — it is re-proven against source here,
     * never trusted as a bearer credential.</p>
     */
    @org.springframework.web.bind.annotation.PostMapping("/session/mode")
    public ResponseEntity<Map<String, Object>> switchWorkMode(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {

        String contextId = str(body.get("contextId"));
        String requestedMode = str(body.get("workMode"));
        String previousJti = str(body.get("previousJti"));

        if (contextId == null || requestedMode == null) {
            return errorBody(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST", "contextId and workMode are required.", requestId, correlationId);
        }

        zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext proven =
                resolutionService.proveContext(actorId, null, contextId);
        if (proven == null) {
            // Generic denial — never discloses whether the contextId is unknown,
            // expired, or belongs to someone else.
            return errorBody(org.springframework.http.HttpStatus.FORBIDDEN,
                    "WORK_SESSION_UNAVAILABLE",
                    "Work access is currently unavailable for the selected context.",
                    requestId, correlationId);
        }
        if (!proven.availableModes().contains(requestedMode)) {
            // Generic denial — never enumerates which modes ARE available.
            return errorBody(org.springframework.http.HttpStatus.FORBIDDEN,
                    "WORK_MODE_UNAVAILABLE",
                    "The requested mode is currently unavailable for this context.",
                    requestId, correlationId);
        }

        Map<String, Object> issueRequest = new LinkedHashMap<>();
        issueRequest.put("tenantId", tenantId);
        issueRequest.put("actorId", actorId);
        issueRequest.put("workMode", requestedMode);
        issueRequest.put("contextId", proven.contextId());
        issueRequest.put("contextKind", proven.contextKind());
        putIfPresent(issueRequest, "facilityId", proven.facilityId());
        putIfPresent(issueRequest, "organisationId", proven.organisationId());
        putIfPresent(issueRequest, "jurisdictionCode", proven.jurisdictionCode());
        putIfPresent(issueRequest, "programmeId", proven.programmeId());
        putIfPresent(issueRequest, "departmentId", proven.departmentId());
        putIfPresent(issueRequest, "wardId", proven.wardId());
        putIfPresent(issueRequest, "workspaceId", proven.workspaceId());
        putIfPresent(issueRequest, "servicePointId", proven.servicePointId());
        putIfPresent(issueRequest, "roleTemplateId", proven.roleTemplateId());
        if (previousJti != null) {
            issueRequest.put("previousJti", previousJti);
        }

        JsonNode issued = tshepoIdentityClient.issueWorkContextToken(issueRequest);
        JsonNode data = issued != null ? issued.path("data") : null;
        if (data == null || data.isMissingNode() || data.isNull()) {
            return errorBody(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "WORK_TOKEN_UNAVAILABLE", "The mode switch could not be completed. Try again.",
                    requestId, correlationId);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("token", text(data, "token"));
        attributes.put("jti", text(data, "jti"));
        attributes.put("expiresAt", text(data, "expiresAt"));
        attributes.put("contextId", proven.contextId());
        attributes.put("workMode", requestedMode);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", actorId, "type", "work-mode-session", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    /**
     * The signed-in person's regulatory appointments (ROM-W2) — drives the regulatory workspace
     * picker. Composition only; org-registry is the SoR. Degrades to an empty list.
     */
    @GetMapping("/regulatory/appointments")
    public ResponseEntity<Map<String, Object>> myRegulatoryAppointments(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {
        JsonNode appointments = orgRegistryClient.listAppointmentsByPerson(actorId);
        List<Object> items = new java.util.ArrayList<>();
        if (appointments != null && appointments.isArray()) {
            for (JsonNode a : appointments) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", text(a, "id"));
                item.put("organizationId", text(a, "organizationId"));
                item.put("roleCode", text(a, "roleCode"));
                item.put("jurisdictionCode", text(a, "jurisdictionCode"));
                item.put("status", text(a, "status"));
                items.add(item);
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", actorId, "type", "regulatory-appointments",
                "attributes", Map.of("appointments", items)));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Start an org-scoped regulatory work session (ROM-W2). The context is the person's ACTIVE
     * org-registry appointment at the requested organisation. Cross-org isolation is enforced at
     * the PDP (authz org dimension); here we only prove the appointment exists and mint the token.
     * Generic denial — never disclose which other orgs the person is appointed at.
     */
    private ResponseEntity<Map<String, Object>> startRegulatorySession(
            String tenantId, String requestId, String correlationId, String actorId,
            String organisationId, String previousJti) {

        JsonNode matched = matchActiveAppointment(orgRegistryClient.listAppointmentsByPerson(actorId), organisationId);
        if (matched == null) {
            return errorBody(org.springframework.http.HttpStatus.FORBIDDEN,
                    "WORK_SESSION_UNAVAILABLE",
                    "Regulatory workspace access is currently unavailable for the selected organisation.",
                    requestId, correlationId);
        }

        Map<String, Object> issueRequest = new LinkedHashMap<>();
        issueRequest.put("tenantId", tenantId);
        issueRequest.put("actorId", actorId);
        issueRequest.put("organisationId", organisationId);
        issueRequest.put("assignmentId", text(matched, "id"));
        issueRequest.put("roleTemplateId", text(matched, "roleCode"));
        // TODO(Phase C): INSPECTION_COMPLIANCE should be selectable for
        // roleCode=INSPECTOR/HPA_INSPECTORATE_OFFICER once the resolver can
        // offer a mode choice; REGULATORY_OPERATIONS is a correct default for
        // every regulatory appointment role in the meantime.
        issueRequest.put("workMode", "REGULATORY_OPERATIONS");
        issueRequest.put("purposeOfUse", "REGULATORY_DUTY");
        // The appointment's jurisdiction bounds the session. It was read here and used only to
        // decorate the response — the minted token never carried it, so the PDP had no jurisdiction
        // to enforce and a province-scoped inspector held a national session.
        String jurisdiction = text(matched, "jurisdictionCode");
        if (jurisdiction != null && !jurisdiction.isBlank()) {
            issueRequest.put("jurisdictionCode", jurisdiction);
        }
        if (previousJti != null) {
            issueRequest.put("previousJti", previousJti);
        }

        JsonNode issued = tshepoIdentityClient.issueWorkContextToken(issueRequest);
        JsonNode data = issued != null ? issued.path("data") : null;
        if (data == null || data.isMissingNode() || data.isNull()) {
            return errorBody(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "WORK_TOKEN_UNAVAILABLE", "The regulatory work session could not be started. Try again.",
                    requestId, correlationId);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("token", text(data, "token"));
        attributes.put("jti", text(data, "jti"));
        attributes.put("expiresAt", text(data, "expiresAt"));
        attributes.put("organisationId", organisationId);
        attributes.put("roleCode", text(matched, "roleCode"));
        attributes.put("jurisdictionCode", jurisdiction);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", actorId, "type", "regulatory-work-session", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /** The requested organisation must be one of the person's ACTIVE appointments. */
    private JsonNode matchActiveAppointment(JsonNode appointments, String organisationId) {
        if (appointments == null || !appointments.isArray()) {
            return null;
        }
        for (JsonNode a : appointments) {
            if ("ACTIVE".equalsIgnoreCase(text(a, "status"))
                    && organisationId.equalsIgnoreCase(text(a, "organizationId"))) {
                return a;
            }
        }
        return null;
    }

    /**
     * Owner-only work-eligibility summary (D-P6, PJ15/PJ17): after sign-in a
     * suspended/lapsed provider sees WHY Work is unavailable and what to do —
     * but ONLY about themselves (session actor), so the generic pre-auth
     * boundary holds. My Life / My Professional are never affected by this.
     */
    @GetMapping("/eligibility")
    public ResponseEntity<Map<String, Object>> workEligibility(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {

        Map<String, Object> attributes = new LinkedHashMap<>();
        JsonNode provider = varapiClient.getProviderByHealthId(actorId);
        if (provider == null || provider.isNull()) {
            attributes.put("linked", false);
            attributes.put("workEligible", false);
            attributes.put("remediation",
                    "No professional profile is linked to your account. "
                            + "Use Request Provider Access to claim or register one.");
        } else {
            String lifecycle = text(provider, "lifecycleStatus");
            String licenceStatus = text(provider, "licenceStatus");
            boolean active = provider.path("active").asBoolean(
                    "ACTIVE".equalsIgnoreCase(text(provider, "status")));
            attributes.put("linked", true);
            attributes.put("lifecycleStatus", lifecycle);
            attributes.put("licenceStatus", licenceStatus);
            attributes.put("workEligible", active);
            attributes.put("remediation", active
                    ? null
                    : "You have signed in successfully. Professional Work access is currently "
                            + "unavailable. Open My Professional to view the status and the next "
                            + "steps (for example, submitting licence renewal evidence).");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", actorId, "type", "work-eligibility", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /** The requested facility (and workspace, when given) must be an ACTIVE assignment. */
    private JsonNode matchAssignment(JsonNode workContext, String facilityId, String workspaceId) {
        if (workContext == null || workContext.isNull()) {
            return null;
        }
        JsonNode assignments = workContext.path("activeAssignments");
        if (!assignments.isArray()) {
            return null;
        }
        for (JsonNode assignment : assignments) {
            if (!facilityId.equalsIgnoreCase(text(assignment, "facilityId"))) {
                continue;
            }
            String assignmentWorkspace = text(assignment, "workspaceId");
            if (workspaceId == null || workspaceId.equalsIgnoreCase(assignmentWorkspace)) {
                return assignment;
            }
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> errorBody(org.springframework.http.HttpStatus status,
                                                          String code, String message,
                                                          String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", Map.of("code", code, "message", message));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(status).body(response);
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private Map<String, Object> toAttributes(JsonNode node) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("workforceProfileId", text(node, "workforceProfileId"));
        attrs.put("impiloHealthId", text(node, "impiloHealthId"));
        attrs.put("activeAssignments", arrayOrEmpty(node, "activeAssignments"));
        attrs.put("checkIn", node.has("checkIn") && !node.get("checkIn").isNull()
                ? node.get("checkIn") : Map.of("state", "CHECKED_OUT"));
        attrs.put("affiliations", arrayOrEmpty(node, "affiliations"));
        attrs.put("requiresContextChooser", node.path("requiresContextChooser").asBoolean(false));
        attrs.put("resolved", node.path("resolved").asBoolean(false));
        return attrs;
    }

    private Map<String, Object> unresolved() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("workforceProfileId", null);
        attrs.put("impiloHealthId", null);
        attrs.put("activeAssignments", List.of());
        attrs.put("checkIn", Map.of("state", "CHECKED_OUT"));
        attrs.put("affiliations", List.of());
        attrs.put("requiresContextChooser", false);
        attrs.put("resolved", false);
        return attrs;
    }

    private Object arrayOrEmpty(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isArray() ? v : List.of();
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v == null || v.isBlank() ? null : v;
    }
}
