package zw.gov.mohcc.impilo.orgregistry.api.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.core.WgvMirrorService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * One-way mirror import from workforce-governance ({@code wgv_organisation}).
 * Idempotent on {@code source_ref} (the wgv organisation id): posting the same
 * organisation twice updates the single mirror row instead of duplicating it.
 */
@RestController
@RequestMapping("/v1/internal/org-registry/mirror")
public class WgvMirrorController {

    private final WgvMirrorService wgvMirrorService;

    public WgvMirrorController(WgvMirrorService wgvMirrorService) {
        this.wgvMirrorService = wgvMirrorService;
    }

    @PostMapping("/wgv")
    public ResponseEntity<Map<String, Object>> mirror(
            @RequestBody OrgRegistryDtos.WgvOrganisationPayload payload) throws Exception {
        WgvMirrorService.MirrorResult result = wgvMirrorService.mirror(tenantId(), payload);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of(
                        "created", result.created(),
                        "organization", result.organization()));
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }
}
