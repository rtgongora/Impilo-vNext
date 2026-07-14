package zw.gov.mohcc.impilo.scheduling.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.scheduling.core.TheatreSchedulerService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/internal/scheduling/theatre-sessions")
public class TheatreSchedulerController {

    private final TheatreSchedulerService service;

    public TheatreSchedulerController(TheatreSchedulerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSession(body));
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String facilityId,
                                          @RequestParam(required = false) String date) {
        return service.listSessions(facilityId, date);
    }

    @GetMapping("/{sessionId}")
    public Map<String, Object> get(@PathVariable String sessionId) {
        return service.getSession(sessionId);
    }

    @PostMapping("/{sessionId}/items")
    public ResponseEntity<Map<String, Object>> addItem(@PathVariable String sessionId,
                                                       @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addItem(sessionId, body));
    }

    @PostMapping("/{sessionId}/reorder")
    @SuppressWarnings("unchecked")
    public Map<String, Object> reorder(@PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        List<String> ordered = (List<String>) body.getOrDefault("orderedItemIds", List.of());
        return service.reorderItems(sessionId, ordered);
    }

    @PostMapping("/{sessionId}/publish")
    public Map<String, Object> publish(@PathVariable String sessionId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        return service.publish(sessionId, body);
    }

    @PostMapping("/{sessionId}/reschedule")
    public Map<String, Object> reschedule(@PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        return service.reschedule(sessionId, body);
    }

    @PostMapping("/{sessionId}/items/{itemId}/cancel")
    public Map<String, Object> cancelItem(@PathVariable String sessionId, @PathVariable String itemId,
                                          @RequestBody Map<String, Object> body) {
        return service.cancelItem(sessionId, itemId, body);
    }

    @ExceptionHandler(TheatreSchedulerService.ConflictException.class)
    public ResponseEntity<Map<String, Object>> onConflict(TheatreSchedulerService.ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.detail());
    }
}
