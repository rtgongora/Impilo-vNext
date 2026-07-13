package zw.gov.mohcc.impilo.costa.api.finance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.FailedMoneyEventEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.FailedMoneyEventRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ops surface for dead-lettered money events: list what was dropped, and
 * replay a dead letter by re-publishing the ORIGINAL payload onto the ORIGINAL
 * topic. Replay is safe because every money consumer is idempotent
 * (costa_idempotency guards duplicates); a replayed event that already
 * processed is simply skipped.
 */
@RestController
@RequestMapping("/internal/v1/finance/failed-money-events")
public class FailedMoneyEventController {

    private static final Logger log = LoggerFactory.getLogger(FailedMoneyEventController.class);

    private final FailedMoneyEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public FailedMoneyEventController(FailedMoneyEventRepository repository,
                                      KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @GetMapping
    public ResponseEntity<List<FailedMoneyEventEntity>> list(
            @RequestParam(value = "status", required = false) String status) {
        TrustContextHolder.require();
        List<FailedMoneyEventEntity> rows = status != null && !status.isBlank()
                ? repository.findByStatusOrderByCreatedAtDesc(status)
                : repository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<FailedMoneyEventEntity> replay(@PathVariable("id") Long id) {
        var ctx = TrustContextHolder.require();
        FailedMoneyEventEntity row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No failed money event with id " + id));
        if (FailedMoneyEventEntity.STATUS_REPLAYED.equals(row.getStatus())) {
            throw new IllegalStateException("Failed money event " + id + " was already replayed");
        }
        if (row.getOriginalTopic() == null || "unknown".equals(row.getOriginalTopic())) {
            throw new IllegalStateException(
                    "Failed money event " + id + " has no original topic recorded; cannot replay");
        }
        kafkaTemplate.send(row.getOriginalTopic(), row.getPayload());
        row.setStatus(FailedMoneyEventEntity.STATUS_REPLAYED);
        row.setReplayedAt(OffsetDateTime.now());
        row.setReplayedBy(ctx.actorId());
        repository.save(row);
        log.info("Replayed failed money event {} onto {} by {}", id, row.getOriginalTopic(), ctx.actorId());
        return ResponseEntity.ok(row);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> notFound(IllegalArgumentException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}
