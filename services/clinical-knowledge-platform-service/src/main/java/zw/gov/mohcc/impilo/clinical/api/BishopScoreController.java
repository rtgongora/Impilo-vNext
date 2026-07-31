package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.maternal.BishopScoreEngine;
import zw.gov.mohcc.impilo.clinical.maternal.BishopScoreService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bishop cervical favourability score over HTTP — assessment only, nothing persisted.
 */
@RestController
@RequestMapping("/internal/v1/clinical/maternal/bishop")
public class BishopScoreController {

    private final BishopScoreService bishop;

    public BishopScoreController(BishopScoreService bishop) {
        this.bishop = bishop;
    }

    public record AssessRequest(
            Object dilation,
            Object effacement,
            Object station,
            Object consistency,
            Object position) {
    }

    @PostMapping("/assess")
    public ResponseEntity<Map<String, Object>> assess(@RequestBody(required = false) AssessRequest request) {
        if (!bishop.available()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "bishop_score_unavailable");
            body.put("message",
                    "The Bishop score engine could not be loaded, so no cervical favourability could be assessed. "
                            + "This is not a statement that the cervix is favourable.");
            return ResponseEntity.status(502).body(Map.of("data", body));
        }

        BishopScoreEngine.Assessment assessment = bishop.assess(new BishopScoreEngine.Input(
                request == null ? null : request.dilation(),
                request == null ? null : request.effacement(),
                request == null ? null : request.station(),
                request == null ? null : request.consistency(),
                request == null ? null : request.position()));

        return ResponseEntity.ok(Map.of("data", toMap(assessment)));
    }

    private static Map<String, Object> toMap(BishopScoreEngine.Assessment assessment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("score", assessment.score());
        data.put("interpretation", assessment.interpretation().name());
        data.put("components", assessment.components());
        data.put("missing", assessment.missing());
        data.put("content_version", assessment.contentVersion());
        return data;
    }
}
