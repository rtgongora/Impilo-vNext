package zw.gov.mohcc.impilo.experience.vashandi;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import java.util.*;

/**
 * Vashandi BFF orchestration — policy precheck, Tshepo audit, honest upstream status.
 */
@Service
public class VashandiService {

    private final VashandiServiceClient vashandiClient;
    private final VashandiPolicyService policyService;
    private final VashandiAuditHelper auditHelper;
    private final SessionExperienceService sessionExperienceService;
    private final VashandiSessionContextResolver sessionContextResolver;

    public VashandiService(VashandiServiceClient vashandiClient,
                           VashandiPolicyService policyService,
                           VashandiAuditHelper auditHelper,
                           SessionExperienceService sessionExperienceService,
                           VashandiSessionContextResolver sessionContextResolver) {
        this.vashandiClient = vashandiClient;
        this.policyService = policyService;
        this.auditHelper = auditHelper;
        this.sessionExperienceService = sessionExperienceService;
        this.sessionContextResolver = sessionContextResolver;
    }

    public VashandiDtos.ActionResponse precheck(
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            String requestId,
            String correlationId,
            String purposeOfUse,
            VashandiDtos.PrecheckRequest body) {
        Map<String, Object> contract = buildContract(actorId, providerId, hasSelectedFacility);
        VashandiDtos.PolicyDecision decision = policyService.evaluate(contract, body);
        if (!decision.allowed()) {
            String message = decision.warnings().isEmpty()
                    ? "This Vashandi action is blocked by policy."
                    : decision.warnings().get(0);
            return VashandiResponses.denied(message, decision, requestId, correlationId);
        }
        auditHelper.emit(
                "vashandi.assignment.prechecked",
                actorId,
                tenantId,
                correlationId,
                purposeOfUse,
                "VashandiAction",
                body.requestedAction(),
                "SUCCESS",
                Map.of("decisionId", decision.decisionId()));
        return VashandiResponses.of(
                "allowed",
                requestId,
                correlationId,
                "Action permitted",
                "Policy precheck passed for your current Vashandi authority.",
                List.of("Continue", "View My Scope"),
                null,
                decision,
                VashandiResponses.auditIngested(correlationId),
                VashandiDtos.IntegrationStatus.LIVE,
                null,
                null
        );
    }

    public VashandiDtos.ActionResponse proxyGet(
            String action,
            String path,
            Map<String, String> queryParams,
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            String requestId,
            String correlationId,
            String purposeOfUse,
            String auditEventType) {
        return proxy(action, () -> vashandiClient.get(path, queryParams), tenantId, actorId, providerId,
                hasSelectedFacility, requestId, correlationId, purposeOfUse, auditEventType, path);
    }

    public VashandiDtos.ActionResponse proxyPost(
            String action,
            String path,
            Object body,
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            String requestId,
            String correlationId,
            String purposeOfUse,
            String auditEventType) {
        return proxy(action, () -> vashandiClient.post(path, body), tenantId, actorId, providerId,
                hasSelectedFacility, requestId, correlationId, purposeOfUse, auditEventType, path);
    }

    public VashandiDtos.ActionResponse proxyPatch(
            String action,
            String path,
            Object body,
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            String requestId,
            String correlationId,
            String purposeOfUse,
            String auditEventType) {
        return proxy(action, () -> vashandiClient.patch(path, body), tenantId, actorId, providerId,
                hasSelectedFacility, requestId, correlationId, purposeOfUse, auditEventType, path);
    }

    private VashandiDtos.ActionResponse proxy(
            String action,
            java.util.function.Supplier<VashandiDtos.UpstreamResult> call,
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            String requestId,
            String correlationId,
            String purposeOfUse,
            String auditEventType,
            String resourceId) {
        VashandiDtos.PrecheckRequest precheck = new VashandiDtos.PrecheckRequest(
                action, null, null, resourceId, null, null, null, Map.of());
        Map<String, Object> contract = buildContract(actorId, providerId, hasSelectedFacility);
        VashandiDtos.PolicyDecision decision = policyService.evaluate(contract, precheck);
        if (!decision.allowed()) {
            String message = decision.warnings().isEmpty()
                    ? "This Vashandi action is blocked by policy."
                    : decision.warnings().get(0);
            return VashandiResponses.denied(message, decision, requestId, correlationId);
        }

        VashandiDtos.UpstreamResult upstream = call.get();
        VashandiResponses.AuditEnvelope audit = auditHelper.emit(
                auditEventType,
                actorId,
                tenantId,
                correlationId,
                purposeOfUse,
                "VashandiResource",
                resourceId,
                upstream.status() == VashandiDtos.IntegrationStatus.LIVE ? "SUCCESS" : "DEGRADED",
                Map.of("integrationStatus", upstream.status().name(),
                        "message", upstream.message() != null ? upstream.message() : ""));

        if (upstream.status() == VashandiDtos.IntegrationStatus.UPSTREAM_UNAVAILABLE) {
            return VashandiResponses.upstreamUnavailable(upstream.message(), requestId, correlationId);
        }

        return VashandiResponses.of(
                "success",
                requestId,
                correlationId,
                "Vashandi operation complete",
                null,
                List.of(),
                null,
                decision,
                audit,
                upstream.status(),
                upstream.message(),
                upstream.data()
        );
    }

    private Map<String, Object> buildContract(String actorId, String providerId, boolean hasSelectedFacility) {
        return new LinkedHashMap<>(sessionExperienceService.buildExperienceContract(actorId, "unknown", providerId, hasSelectedFacility));
    }
}
