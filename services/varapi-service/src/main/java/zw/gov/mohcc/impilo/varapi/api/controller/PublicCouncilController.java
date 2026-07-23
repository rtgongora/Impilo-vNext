package zw.gov.mohcc.impilo.varapi.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.core.CouncilService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProfessionalRegisterEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProfessionalRegisterRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public councils / professional-registers reference (gateway public lane).
 *
 * <p>Mirrors the tuso/varapi public pattern (template:
 * {@link PublicPractitionerVerificationController}): a {@code Public*Controller}
 * is the ONLY service-side surface a BFF public controller may call
 * (docs/architecture/gateway-public-lane-security-adr.md). Discloses ONLY
 * institutional, non-PII reference facts about the statutory regulatory
 * councils (e.g. MDPCZ, NMCZ, PCAZ) and the professional registers they
 * maintain — the kind of catalogue a citizen would find on a council's own
 * public website.</p>
 *
 * <p>Every council view is built from an explicit allow-list
 * ({@code councilCode, name, councilType, description, status, website, email,
 * phone}). Internal keys — surrogate id, tenant id, org-registry linkage,
 * registration-number pattern and audit timestamps — are never emitted. The
 * register view is likewise limited to {@code registerCode, name, status}.
 * There is no free-text or fuzzy search on this lane (enumeration resistance),
 * and an unknown {@code councilCode} yields the identical response shape with an
 * empty {@code registers} list, so the endpoint exposes no existence oracle.</p>
 *
 * <p>Councils and registers are seeded under the REGISTRY-plane tenant
 * {@code 00000000-0000-0000-0000-000000000001}. Because the underlying reads
 * ({@link CouncilService#listCouncils()} and the register repository) resolve
 * the tenant from {@link TrustContextHolder}, this controller supplies that
 * registry-plane tenant the same way the trust context is normally carried:
 * it derives a registry-plane {@link TrustContext} from the request context and
 * installs it on the holder for the duration of the read, restoring the
 * original afterwards.</p>
 */
@RestController
@RequestMapping("/v1/public/councils")
public class PublicCouncilController {

    private static final Logger log = LoggerFactory.getLogger(PublicCouncilController.class);

    /** REGISTRY-plane tenant under which councils/registers are seeded. */
    private static final UUID REGISTRY_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CouncilService councilService;
    private final CouncilRepository councilRepository;
    private final ProfessionalRegisterRepository registerRepository;

    public PublicCouncilController(CouncilService councilService,
                                   CouncilRepository councilRepository,
                                   ProfessionalRegisterRepository registerRepository) {
        this.councilService = councilService;
        this.councilRepository = councilRepository;
        this.registerRepository = registerRepository;
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listCouncils() {
        TrustContext ctx = TrustContextHolder.require();
        TrustContext registryCtx = withRegistryTenant(ctx);

        List<Map<String, Object>> body;
        TrustContextHolder.set(registryCtx);
        try {
            body = councilService.listCouncils().stream()
                    .map(PublicCouncilController::toCouncilView)
                    .toList();
        } finally {
            TrustContextHolder.set(ctx);
        }

        log.info("Public council list [count={}] correlationId={}", body.size(), ctx.correlationId());
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }

    @GetMapping("/{councilCode}/registers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registersForCouncil(
            @PathVariable String councilCode) {
        TrustContext ctx = TrustContextHolder.require();

        CouncilEntity council = councilRepository.findByCouncilCode(councilCode).orElse(null);

        // Uniform empty shape on a miss — no existence oracle beyond an empty list.
        if (council == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("councilCode", councilCode);
            empty.put("councilName", null);
            empty.put("registers", List.of());
            log.info("Public council registers [councilCode={}, found=false] correlationId={}",
                    councilCode, ctx.correlationId());
            return ResponseEntity.ok(ApiResponse.ok(empty, ctx.correlationId().toString()));
        }

        List<Map<String, Object>> registers = registerRepository
                .findByTenantIdAndCouncilIdOrderByRegisterCodeAsc(REGISTRY_TENANT_ID, council.getId())
                .stream()
                .map(PublicCouncilController::toRegisterView)
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("councilCode", council.getCouncilCode());
        body.put("councilName", council.getName());
        body.put("registers", registers);

        log.info("Public council registers [councilCode={}, found=true, count={}] correlationId={}",
                councilCode, registers.size(), ctx.correlationId());
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }

    // ---- Allow-listed, non-PII views ----

    private static Map<String, Object> toCouncilView(CouncilEntity c) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("councilCode", c.getCouncilCode());
        view.put("name", c.getName());
        view.put("councilType", c.getCouncilType());
        view.put("description", c.getDescription());
        view.put("status", c.getStatus());
        view.put("website", c.getWebsite());
        view.put("email", c.getEmail());
        view.put("phone", c.getPhone());
        return view;
    }

    private static Map<String, Object> toRegisterView(ProfessionalRegisterEntity r) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("registerCode", r.getRegisterCode());
        view.put("name", r.getName());
        view.put("status", r.getStatus());
        return view;
    }

    /** Derive a registry-plane trust context (same request identity, seeded tenant). */
    private static TrustContext withRegistryTenant(TrustContext ctx) {
        return new TrustContext(
                REGISTRY_TENANT_ID,
                ctx.actorId(),
                ctx.actorType(),
                ctx.purposeOfUse(),
                ctx.deviceFingerprint(),
                ctx.correlationId(),
                ctx.facilityId(),
                ctx.workspaceId(),
                ctx.shiftId(),
                ctx.mode());
    }
}
