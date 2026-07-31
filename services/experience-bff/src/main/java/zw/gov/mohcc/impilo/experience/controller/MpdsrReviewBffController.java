package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.RitoServiceClient;

import java.util.Map;

/**
 * BFF proxy for MPDSR maternal/perinatal death reviews — QI/ops lane only.
 */
@RestController
@RequestMapping("/internal/v1/rito/mpdsr")
public class MpdsrReviewBffController {

    private final RitoServiceClient rito;

    public MpdsrReviewBffController(RitoServiceClient rito) {
        this.rito = rito;
    }

    @PostMapping("/reviews/from-death-event")
    public ResponseEntity<JsonNode> fromDeathEvent(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rito.mpdsrFromDeathEvent(body));
    }

    @GetMapping("/reviews")
    public ResponseEntity<JsonNode> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(rito.listMpdsrReviews(status));
    }

    @GetMapping("/reviews/{id}")
    public ResponseEntity<JsonNode> get(@PathVariable String id) {
        return ResponseEntity.ok(rito.getMpdsrReview(id));
    }

    @PostMapping("/reviews/{id}/committee-meetings")
    public ResponseEntity<JsonNode> committeeMeeting(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rito.mpdsrReviewAction(id, "committee-meetings", body));
    }

    @PostMapping("/reviews/{id}/findings")
    public ResponseEntity<JsonNode> finding(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rito.mpdsrReviewAction(id, "findings", body));
    }

    @PostMapping("/reviews/{id}/actions")
    public ResponseEntity<JsonNode> action(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rito.mpdsrReviewAction(id, "actions", body));
    }

    @PostMapping("/reviews/{id}/readiness-task")
    public ResponseEntity<JsonNode> readinessTask(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rito.mpdsrReviewAction(id, "readiness-task", body != null ? body : Map.of()));
    }

    @PostMapping("/reviews/{id}/close")
    public ResponseEntity<JsonNode> close(@PathVariable String id) {
        return ResponseEntity.ok(rito.mpdsrReviewAction(id, "close", Map.of()));
    }
}
