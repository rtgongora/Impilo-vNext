package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;

/**
 * Medical episodes (PCT V101) — read proxy for clerking continuity.
 * Failed upstream must not render as "no previous episodes".
 */
@RestController
@RequestMapping("/internal/v1/medical-episodes")
public class MedicalEpisodesController {

    private static final Logger log = LoggerFactory.getLogger(MedicalEpisodesController.class);

    private final PctServiceClient pctClient;

    public MedicalEpisodesController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "open_only", defaultValue = "false") boolean openOnly) {
        try {
            JsonNode data = pctClient.listMedicalEpisodes(subjectCpid, openOnly);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("PCT listMedicalEpisodes failed for subject={}: {}", subjectCpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "medical_episodes_unavailable",
                    "message", "Medical episodes could not be retrieved. Do not treat this as an "
                            + "absence of previous episodes of care.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
