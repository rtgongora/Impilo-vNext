package zw.gov.mohcc.impilo.channels.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.channels.api.dto.SessionResponse;
import zw.gov.mohcc.impilo.channels.service.SessionService;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/channels/sessions/snapshot")
public class SnapshotController {

    private final SessionService sessionService;

    public SnapshotController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> snapshot() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(sessionService.listSessions(ctx));
    }
}
