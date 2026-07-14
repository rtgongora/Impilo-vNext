package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.recognition.RecognitionCompositionService;

import java.util.Map;

/**
 * Health-worker recognition composition for experience surfaces.
 *
 * <p>GET /internal/v1/experience/recognition/{healthId} — resolves BOTH
 * recognition sources (VARAPI licensed providers + Vashandi workforce) into
 * {recognised, recognitionClass, profession, cadre, licenceStatus}.</p>
 *
 * <p>Display-only: powers the "Recognised Health Provider / Worker" badge
 * chip and the profile benefits card. It must never reorder queues or grant
 * clinical priority. Negative answers are generic (anti-enumeration holds
 * end-to-end because both upstreams are enumeration-resistant).</p>
 */
@RestController
@RequestMapping("/internal/v1/experience/recognition")
public class RecognitionController {

    private final RecognitionCompositionService recognitionService;

    public RecognitionController(RecognitionCompositionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    @GetMapping("/{healthId}")
    public ResponseEntity<Map<String, Object>> recognition(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        RecognitionCompositionService.RecognitionView view = recognitionService.resolve(healthId);
        return ResponseEntity.ok(Map.of(
                "data", view,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
