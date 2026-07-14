package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.TheatreTeamService;

import java.util.Map;

/** Theatre case-team API (VASHANDI SoR). Consumed by inpatient readiness (TEAM). */
@RestController
public class VashandiTheatreTeamController {

    private final TheatreTeamService service;

    public VashandiTheatreTeamController(TheatreTeamService service) {
        this.service = service;
    }

    @PostMapping("/v1/internal/vashandi/theatre-teams")
    public ResponseEntity<Map<String, Object>> assemble(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assembleTeam(body));
    }

    @GetMapping("/v1/internal/vashandi/theatre-teams/{teamId}")
    public Map<String, Object> get(@PathVariable String teamId) {
        return service.getTeam(teamId);
    }

    @PostMapping("/v1/internal/vashandi/theatre-teams/{teamId}/members")
    public ResponseEntity<Map<String, Object>> addMember(@PathVariable String teamId,
                                                        @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addMember(teamId, body));
    }

    @PostMapping("/v1/internal/vashandi/theatre-teams/{teamId}/confirm")
    public Map<String, Object> confirm(@PathVariable String teamId) {
        return service.confirmTeam(teamId);
    }

    @PostMapping("/v1/internal/vashandi/theatre-teams/{teamId}/stand-down")
    public Map<String, Object> standDown(@PathVariable String teamId,
                                        @RequestBody(required = false) Map<String, Object> body) {
        return service.standDown(teamId, body);
    }
}
