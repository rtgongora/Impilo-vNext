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
import zw.gov.mohcc.impilo.channels.api.dto.EscalateRequest;
import zw.gov.mohcc.impilo.channels.api.dto.SessionResponse;
import zw.gov.mohcc.impilo.channels.service.SessionService;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/channels/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> listSessions() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(sessionService.listSessions(ctx));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable UUID id) {
        RequestContextHolder.require();
        return ResponseEntity.ok(sessionService.getSession(id));
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateSessionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        SessionResponse response = sessionService.createSession(request, ctx, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{sessionId}/escalate")
    public ResponseEntity<SessionResponse> escalateSession(
            @PathVariable UUID sessionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EscalateRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        SessionResponse response = sessionService.escalateSession(
                sessionId, request.agentId(), request.reason(), ctx, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
