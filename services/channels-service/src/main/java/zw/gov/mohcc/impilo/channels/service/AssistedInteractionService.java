package zw.gov.mohcc.impilo.channels.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.channels.api.dto.AssistedInteractionResponse;
import zw.gov.mohcc.impilo.channels.api.dto.CreateAssistedInteractionRequest;
import zw.gov.mohcc.impilo.channels.domain.AssistedInteractionEntity;
import zw.gov.mohcc.impilo.channels.domain.ChannelSessionEntity;
import zw.gov.mohcc.impilo.channels.repository.AssistedInteractionRepository;
import zw.gov.mohcc.impilo.channels.repository.ChannelSessionRepository;
import zw.gov.mohcc.impilo.companion.context.RequestContext;

import java.util.Map;

@Service
public class AssistedInteractionService {

    private final AssistedInteractionRepository interactionRepository;
    private final ChannelSessionRepository sessionRepository;
    private final OutboxService outboxService;

    public AssistedInteractionService(AssistedInteractionRepository interactionRepository,
                                      ChannelSessionRepository sessionRepository,
                                      OutboxService outboxService) {
        this.interactionRepository = interactionRepository;
        this.sessionRepository = sessionRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public AssistedInteractionResponse record(CreateAssistedInteractionRequest request,
                                              RequestContext ctx, String idempotencyKey) {
        ChannelSessionEntity session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new SessionService.SessionNotFoundException(request.sessionId()));

        AssistedInteractionEntity interaction = new AssistedInteractionEntity(
                session.getId(), ctx.tenantId(), ctx.podId(), request.agentId());
        interaction.setClientId(request.clientId());
        interaction.setNotesJson(request.notesJson());

        interactionRepository.save(interaction);

        outboxService.persistEvent(ctx,
                "impilo.channels.assisted.interaction.recorded.v1",
                "AssistedInteraction", interaction.getId().toString(),
                "AssistedInteraction", interaction.getId().toString(),
                idempotencyKey,
                Map.of(
                        "interaction_id", interaction.getId().toString(),
                        "session_id", session.getId().toString(),
                        "agent_id", interaction.getAgentId(),
                        "client_id", interaction.getClientId() != null ? interaction.getClientId() : "",
                        "tenant_id", ctx.tenantId()
                ));

        return toResponse(interaction);
    }

    private AssistedInteractionResponse toResponse(AssistedInteractionEntity entity) {
        return new AssistedInteractionResponse(
                entity.getId(),
                entity.getSessionId(),
                entity.getAgentId(),
                entity.getClientId(),
                entity.getCreatedAt()
        );
    }
}
