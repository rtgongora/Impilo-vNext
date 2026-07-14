package zw.gov.mohcc.impilo.inpatient.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inpatient.core.TheatreReadinessBoardService;

import java.util.Map;
import java.util.UUID;

/**
 * ── Wave 2 readiness board ── theatre-day readiness board API. Composed by the
 * experience-bff into the per-patient board.
 */
@RestController
@RequestMapping("/internal/v1/theatre/cases")
public class TheatreReadinessBoardController {

    private final TheatreReadinessBoardService boardService;

    public TheatreReadinessBoardController(TheatreReadinessBoardService boardService) {
        this.boardService = boardService;
    }

    @PostMapping("/{id}/board-readiness")
    public Map<String, Object> boardReadiness(@PathVariable String id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        return boardService.evaluateBoard(UUID.fromString(id), body != null ? body : Map.of());
    }

    @PostMapping("/{id}/resolve-blocker")
    public Map<String, Object> resolveBlocker(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return boardService.resolveBlocker(UUID.fromString(id), body);
    }
}
