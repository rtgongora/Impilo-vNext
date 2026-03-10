package zw.gov.mohcc.impilo.channels.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.channels.api.dto.CreateSessionRequest;
import zw.gov.mohcc.impilo.channels.api.dto.SessionResponse;
import zw.gov.mohcc.impilo.channels.domain.ChannelSessionEntity;
import zw.gov.mohcc.impilo.channels.repository.ChannelSessionRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/sessions")
public class SessionController {

    private final ChannelSessionRepository sessionRepository;

    public SessionController(ChannelSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> listSessions(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<SessionResponse> sessions = sessionRepository
                .findByTenantIdAndSessionState(UUID.fromString(tenantId), "ACTIVE")
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable UUID id) {
        return sessionRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @Valid @RequestBody CreateSessionRequest request) {
        ChannelSessionEntity session = new ChannelSessionEntity(
                UUID.fromString(tenantId), podId, request.channelType());
        session.setClientId(request.clientId());
        sessionRepository.save(session);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session));
    }

    private SessionResponse toResponse(ChannelSessionEntity entity) {
        return new SessionResponse(
                entity.getId(),
                entity.getChannelType(),
                entity.getSessionState(),
                entity.getClientId(),
                entity.getAgentId(),
                entity.getStartedAt(),
                entity.getLastActivity(),
                entity.getClosedAt()
        );
    }
}
