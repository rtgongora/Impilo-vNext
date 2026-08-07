package zw.gov.mohcc.impilo.tshepo.audit.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventResponse;
import zw.gov.mohcc.impilo.tshepo.audit.core.AuditChainService;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;

/**
 * Ingest endpoint for audit events from other services.
 *
 * <h2>Who the event says it was</h2>
 * <p>Until this class was changed, {@code actorId}, {@code actorType} and {@code tenantId} were read
 * from the request <b>body</b>, and the endpoint was {@code permitAll} in {@code SecurityConfig}.
 * Together those two facts meant anything that could reach the service could append an audit record
 * naming an arbitrary actor, action and outcome — into an arbitrary tenant's hash chain. The chain
 * would verify perfectly afterwards: hash chaining proves nobody edited a row that was written, and
 * says nothing at all about whether the row was true when it was written.</p>
 *
 * <p>That matters more here than at an ordinary write surface. This is the evidence every other
 * guard in the estate is checked against. A forged row is not just a false record; it is a false
 * record that later reads as proof.</p>
 *
 * <p>So the identity on the persisted event is now taken from the trust context — the headers the
 * PDP stamps and Envoy's {@code allowed_upstream_headers} lets through in place of the client's own
 * values — and the body's actor fields are ignored. A caller can still describe <em>what happened</em>
 * (event type, action, outcome, subject, resource); it can no longer choose <em>who it was</em>.</p>
 *
 * <p>The body's actor fields are deliberately still accepted and validated rather than removed from
 * {@link AuditEventRequest}: the same record is the Kafka consumer's and the Keycloak recorder's
 * input, and those paths are not HTTP and legitimately carry their own actor. Removing the fields
 * would break them; ignoring the fields <em>at this boundary</em> is what closes the forgery.</p>
 */
@RestController
@RequestMapping("/v1/audit/events")
public class AuditIngestController {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestController.class);

    private final AuditChainService auditChainService;

    public AuditIngestController(AuditChainService auditChainService) {
        this.auditChainService = auditChainService;
    }

    /**
     * POST /v1/audit/events — append an audit event to the hash chain, attributed to the caller.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AuditEventResponse>> ingestEvent(
            @Valid @RequestBody AuditEventRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        requireAttributableCaller(ctx);
        AuditEventRequest attributed = attributeToCaller(request, ctx);

        if (!equalIgnoringNull(request.actorId(), attributed.actorId())
                || !equalIgnoringNull(request.actorType(), attributed.actorType())
                || !equalIgnoringNull(request.tenantId(), attributed.tenantId())) {
            // Not a refusal: the event is still recorded, against the caller's real identity. It is
            // logged because a body that disagrees with the trust context is either a client that
            // has not been updated or an attempt to attribute an action to someone else, and both
            // are worth being able to find later.
            log.warn("Audit ingest body disagreed with the trust context and was overridden — "
                            + "body actor={}/{} tenant={}, context actor={}/{} tenant={}, eventType={}",
                    request.actorId(), request.actorType(), request.tenantId(),
                    attributed.actorId(), attributed.actorType(), attributed.tenantId(),
                    request.eventType());
        }

        log.info("Ingesting audit event: type={}, actor={}, correlationId={}",
                attributed.eventType(), attributed.actorId(), attributed.correlationId());

        AuditEventEntity saved = auditChainService.appendEvent(attributed);

        AuditEventResponse response = new AuditEventResponse(
                saved.getId(),
                saved.getTenantId(),
                saved.getEventType(),
                saved.getActorId(),
                saved.getActorType(),
                saved.getSubjectRef(),
                saved.getResourceType(),
                saved.getResourceId(),
                saved.getAction(),
                saved.getOutcome(),
                saved.getPurposeOfUse(),
                saved.getFacilityId(),
                saved.getCorrelationId(),
                saved.getDetail(),
                saved.getPreviousHash(),
                saved.getEntryHash(),
                saved.getSequenceNumber(),
                saved.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, saved.getCorrelationId().toString()));
    }

    /**
     * Refuse an audit write that cannot be attributed to anyone.
     *
     * <p>{@link zw.gov.mohcc.impilo.shared.auth.TrustContextFilter} never rejects: it reads the trust
     * headers and leaves absent ones null. So "the context is set" is not the same as "the context
     * identifies a caller", and without this check an identity-less request would be recorded as an
     * audit row with a null actor and — worse — a null tenant, which is not a row in any tenant's
     * chain. An unattributable audit event is not a weaker record, it is a false one.</p>
     */
    private static void requireAttributableCaller(TrustContext ctx) {
        boolean tenantPresent = ctx.tenantId() != null;
        boolean actorPresent = ctx.actorId() != null && !ctx.actorId().isBlank();
        boolean actorTypePresent = ctx.actorType() != null && !ctx.actorType().isBlank();
        if (tenantPresent && actorPresent && actorTypePresent) {
            return;
        }
        log.warn("Refused audit ingest — the caller is not attributable: tenant={}, actor={}, actorType={}",
                ctx.tenantId(), ctx.actorId(), ctx.actorType());
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Appending an audit event requires an attributable caller: "
                        + TrustContext.H_TENANT_ID + ", " + TrustContext.H_ACTOR_ID + " and "
                        + TrustContext.H_ACTOR_TYPE + " must all be present. This request presents "
                        + (tenantPresent ? "" : "no tenant, ")
                        + (actorPresent ? "" : "no actor id, ")
                        + (actorTypePresent ? "" : "no actor type, ")
                        + "so the event could not be attributed to anyone.");
    }

    /**
     * Replace the body's identity claims with the caller's. Everything describing what happened is
     * kept as sent.
     */
    private static AuditEventRequest attributeToCaller(AuditEventRequest request, TrustContext ctx) {
        return new AuditEventRequest(
                ctx.tenantId(),
                request.eventType(),
                ctx.actorId(),
                ctx.actorType(),
                request.subjectRef(),
                request.resourceType(),
                request.resourceId(),
                request.action(),
                request.outcome(),
                request.purposeOfUse(),
                request.facilityId(),
                request.correlationId() != null ? request.correlationId() : ctx.correlationId(),
                request.detail()
        );
    }

    private static boolean equalIgnoringNull(Object a, Object b) {
        return a == null || a.equals(b);
    }
}
