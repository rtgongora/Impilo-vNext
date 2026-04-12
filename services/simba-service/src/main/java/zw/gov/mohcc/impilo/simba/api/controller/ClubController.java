package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.ClubService;
import zw.gov.mohcc.impilo.simba.persistence.entity.ClubEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.ClubMemberEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @PostMapping
    public ResponseEntity<ClubEntity> post(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        ClubEntity c = new ClubEntity();
        c.setTenantId(tenantId);
        c.setName(required(body, "name"));
        if (body.get("description") instanceof String d) {
            c.setDescription(d);
        }
        c.setClubType(required(body, "club_type"));
        if (body.get("visibility") instanceof String v) {
            c.setVisibility(v);
        }
        if (body.get("max_members") != null) {
            c.setMaxMembers(Integer.parseInt(body.get("max_members").toString()));
        }
        if (body.get("created_by") instanceof String cb) {
            c.setCreatedBy(cb);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(clubService.create(c));
    }

    @GetMapping
    public List<ClubEntity> list(@RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return clubService.list(tenantId);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ClubMemberEntity> join(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("id") UUID clubId,
            @RequestBody Map<String, Object> body) {
        String personCpid = required(body, "person_cpid");
        String role = body.get("role") instanceof String r ? r : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(clubService.join(clubId, tenantId, personCpid, role));
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leave(
            @PathVariable("id") UUID clubId,
            @RequestParam("person_cpid") String personCpid) {
        clubService.leave(clubId, personCpid);
        return ResponseEntity.noContent().build();
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
