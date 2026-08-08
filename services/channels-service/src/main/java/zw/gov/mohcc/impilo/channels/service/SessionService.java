package zw.gov.mohcc.impilo.channels.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.channels.api.dto.CreateSessionRequest;
import zw.gov.mohcc.impilo.channels.api.dto.SessionResponse;
import zw.gov.mohcc.impilo.channels.domain.ChannelSessionEntity;
import zw.gov.mohcc.impilo.channels.repository.ChannelSessionRepository;
import zw.gov.mohcc.impilo.companion.context.RequestContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SessionService {

    private final ChannelSessionRepository sessionRepository;
    private final OutboxService outboxService;

    public SessionService(ChannelSessionRepository sessionRepository, OutboxService outboxService) {
        this.sessionRepository = sessionRepository;
        this.outboxService = outboxService;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(RequestContext ctx) {
        return sessionRepository.findByTenantId(ctx.tenantId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(UUID id) {
        return sessionRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new SessionNotFoundException(id));
    }

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request, RequestContext ctx,
                                         String idempotencyKey) {
        ChannelSessionEntity session = new ChannelSessionEntity(
                ctx.tenantId(), ctx.podId(), request.channelType());
        session.setSubjectRef(request.subjectRef());
        session.setFacilityId(request.facilityId());

        sessionRepository.save(session);

        outboxService.persistEvent(ctx,
                "impilo.channels.session.started.v1",
                "ChannelSession", session.getId().toString(),
                "ChannelSession", session.getId().toString(),
                idempotencyKey,
                Map.of(
                        "session_id", session.getId().toString(),
                        "channel_type", session.getChannelType(),
                        "status", session.getStatus(),
                        "subject_ref", session.getSubjectRef() != null ? session.getSubjectRef() : "",
                        "tenant_id", ctx.tenantId(),
                        "pod_id", ctx.podId()
                ));

        return toResponse(session);
    }

    @Transactional
    public SessionResponse escalateSession(UUID sessionId, String agentId, String reason,
                                           RequestContext ctx, String idempotencyKey) {
        ChannelSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!"ACTIVE".equals(session.getStatus())) {
            throw new IllegalStateException(
                    "Session " + sessionId + " is " + session.getStatus() + "; only ACTIVE sessions can be escalated");
        }

        session.setStatus("ESCALATED");
        session.setAgentId(agentId);
        sessionRepository.save(session);

        outboxService.persistEvent(ctx,
                "impilo.channels.escalation.requested.v1",
                "ChannelSession", session.getId().toString(),
                "ChannelSession", session.getId().toString(),
                idempotencyKey + ":escalation.requested",
                Map.of(
                        "session_id", session.getId().toString(),
                        "agent_id", agentId,
                        "reason", reason != null ? reason : "",
                        "tenant_id", ctx.tenantId()
                ));

        // escalation.completed.v1 is deliberately NOT emitted here.
        //
        // It used to be written in this same transaction, one statement after
        // escalation.requested — so "an agent has picked this up" was asserted at the instant of
        // asking, for an agent id supplied by the caller, with no agent notified and no acceptance
        // recorded. A consumer counting completed-vs-requested would have measured 100% handling
        // of every escalation, always. Requesting is all that has happened, and requesting is all
        // this event stream may claim.
        //
        // The completion event belongs to whatever records an agent actually accepting the
        // session; when that exists, it emits escalation.completed.v1 at that point.

        return toResponse(session);
    }

    private SessionResponse toResponse(ChannelSessionEntity entity) {
        return new SessionResponse(
                entity.getId(),
                entity.getChannelType(),
                entity.getStatus(),
                entity.getSubjectRef(),
                entity.getAgentId(),
                entity.getFacilityId(),
                entity.getStartedAt(),
                entity.getLastActivity(),
                entity.getClosedAt()
        );
    }

    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(UUID id) {
            super("Session not found: " + id);
        }
    }
}
