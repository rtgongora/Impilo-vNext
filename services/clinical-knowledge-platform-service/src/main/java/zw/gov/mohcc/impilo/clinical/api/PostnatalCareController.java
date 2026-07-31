package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.danger.DangerSignEvaluationService;
import zw.gov.mohcc.impilo.clinical.maternal.MaternalDangerSignService;
import zw.gov.mohcc.impilo.clinical.maternal.PostnatalNewbornService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/clinical/maternal/pnc")
public class PostnatalCareController {

    static final String PNC_MATERNAL_PACK = "clinical/rmnp-pnc-maternal-danger-signs.json";

    private final MaternalDangerSignService maternalDangerSigns;
    private final PostnatalNewbornService postnatalNewborn;

    public PostnatalCareController(MaternalDangerSignService maternalDangerSigns,
                                   PostnatalNewbornService postnatalNewborn) {
        this.maternalDangerSigns = maternalDangerSigns;
        this.postnatalNewborn = postnatalNewborn;
    }

    public record MaternalAssessRequest(Map<String, Object> facts, Boolean screeningComplete) {}
    public record NewbornAssessRequest(Integer ageDays, Map<String, Object> facts, Boolean screeningComplete) {}

    @PostMapping("/maternal/assess")
    public ResponseEntity<Map<String, Object>> assessMaternal(@RequestBody(required = false) MaternalAssessRequest request) {
        Map<String, Object> facts = request == null || request.facts() == null ? Map.of() : request.facts();
        boolean complete = request != null && Boolean.TRUE.equals(request.screeningComplete());
        MaternalDangerSignService.Assessment a = maternalDangerSigns.evaluate(PNC_MATERNAL_PACK, facts, complete);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alerts", a.alerts().stream().map(PostnatalCareController::alert).toList());
        data.put("top_line_action", a.topLineAction());
        data.put("not_assessed", a.notAssessed());
        data.put("incomplete", a.incomplete());
        data.put("screening_complete", a.screeningComplete());
        data.put("no_danger_signs_found", a.noDangerSignsFound());
        data.put("note", a.note());
        return ResponseEntity.ok(Map.of("data", data));
    }

    @PostMapping("/newborn/assess")
    public ResponseEntity<Map<String, Object>> assessNewborn(@RequestBody(required = false) NewbornAssessRequest request) {
        Integer ageDays = request == null ? null : request.ageDays();
        Map<String, Object> facts = request == null || request.facts() == null ? Map.of() : request.facts();
        boolean complete = request != null && Boolean.TRUE.equals(request.screeningComplete());
        PostnatalNewbornService.Assessment a = postnatalNewborn.assess(ageDays, facts, complete);
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> psbi = new LinkedHashMap<>(a.psbi().toMap());
        psbi.put("no_danger_signs_found", a.psbi().assessed() && !a.psbi().incomplete() && a.psbi().alerts().isEmpty());
        data.put("psbi", psbi);
        data.put("pnc_specific_alerts", a.pncSpecificAlerts().stream().map(PostnatalCareController::alert).toList());
        data.put("top_line_action", a.topLineAction());
        data.put("systemic_emergency", a.systemicEmergency());
        data.put("incomplete", a.incomplete());
        data.put("note", a.note());
        data.put("psbi_delegation_note", "PSBI evaluated by the single young-infant definition — not restated here.");
        return ResponseEntity.ok(Map.of("data", data));
    }

    private static Map<String, Object> alert(MaternalDangerSignService.Alert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.code());
        m.put("name", a.name());
        m.put("severity", a.severity());
        m.put("required_action", a.requiredAction());
        return m;
    }
}
