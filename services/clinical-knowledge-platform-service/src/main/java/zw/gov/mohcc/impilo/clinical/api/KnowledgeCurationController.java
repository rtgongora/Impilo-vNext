package zw.gov.mohcc.impilo.clinical.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.clinical.curation.KnowledgeCurationService;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Knowledge curation")
@RestController
@RequestMapping("/internal/v1/clinical/curation")
public class KnowledgeCurationController {

    private final KnowledgeCurationService knowledgeCurationService;

    public KnowledgeCurationController(KnowledgeCurationService knowledgeCurationService) {
        this.knowledgeCurationService = knowledgeCurationService;
    }

    @Operation(summary = "List knowledge review queue items")
    @GetMapping("/review-items")
    public ResponseEntity<Map<String, Object>> listReviewItems(
            @RequestParam(name = "status", defaultValue = "PROPOSED") String status) {
        return ResponseEntity.ok(Map.of("data", knowledgeCurationService.listReviewItems(status)));
    }

    @Operation(summary = "Approve or reject a proposed extraction item")
    @PostMapping("/review-items/{id}/decision")
    public ResponseEntity<Map<String, Object>> decide(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String decision = body.get("decision") != null ? body.get("decision").toString() : "";
        String reviewer = body.get("reviewer") != null ? body.get("reviewer").toString() : null;
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        Map<String, Object> data = knowledgeCurationService.decide(id, decision, reviewer, notes);
        return ResponseEntity.ok(Map.of("data", data));
    }
}
