package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.ContactHasher;
import zw.gov.mohcc.impilo.vito.core.ImpiloIdAliasService;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Private, server-side identity resolution for the Trust Core (Identity Journey
 * Doctrine §2). INTERNAL-only. Resolves a presented identifier to a Health ID —
 * or a uniform miss — and returns <b>nothing but the healthId</b>: no candidate
 * list, no demographics, no existence signal. VITO is the pepper + alias-vault
 * holder, so raw values are hashed here and never leave.
 *
 * <p>Callers: tshepo-identity {@code SilentIdentifierResolutionService} (login
 * hardening) and {@code IdResolutionService} (full resolve chain). The
 * anti-enumeration timing floor is applied by the calling service around the
 * whole resolution; this endpoint's contribution is a uniform 200 response shape
 * on hit and miss alike ({@code {resolved, data:{healthId}}}).</p>
 */
@RestController
@RequestMapping("/v1/registry")
public class RegistryResolveController {

    private final ClientRepository clientRepository;
    private final ImpiloIdAliasService aliasService;
    private final ContactHasher contactHasher;
    private final zw.gov.mohcc.impilo.vito.core.RecordClaimService recordClaimService;

    public RegistryResolveController(ClientRepository clientRepository,
                                     ImpiloIdAliasService aliasService,
                                     ContactHasher contactHasher,
                                     zw.gov.mohcc.impilo.vito.core.RecordClaimService recordClaimService) {
        this.clientRepository = clientRepository;
        this.aliasService = aliasService;
        this.contactHasher = contactHasher;
        this.recordClaimService = recordClaimService;
    }

    public record ResolveRequest(UUID tenantId, String kind, String value) {}
    public record ClaimStartRequest(UUID tenantId, UUID healthId) {}
    public record ClaimVerifyRequest(UUID challengeId, String code, String accountRef) {}

    /**
     * Resolve an Impilo ID or Health ID to a Health ID. Kinds: IMPILO_ID (alias
     * vault), HEALTH_ID (existence check). Uniform 200 with {@code data.healthId}
     * null on miss.
     */
    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolve(@RequestBody ResolveRequest req) {
        internalOnly();
        UUID tenantId = req.tenantId();
        String kind = req.kind() == null ? "" : req.kind().trim().toUpperCase(Locale.ROOT);
        String value = req.value() == null ? "" : req.value().trim();
        UUID healthId = null;
        if (tenantId != null && !value.isBlank()) {
            healthId = switch (kind) {
                case "IMPILO_ID" -> aliasService.resolve(tenantId, "IMPILO_ID", value).orElse(null);
                case "LEGACY_PHID" -> aliasService.resolve(tenantId, "LEGACY_PHID", value).orElse(null);
                case "HEALTH_ID" -> parseHealthIdIfPresent(tenantId, value);
                default -> null;
            };
        }
        return uniform(healthId);
    }

    /**
     * Private contact matching: phone/email → Health ID (Identity Journey Doctrine
     * §2). The raw contact is hashed here; only healthId (or a uniform miss) is
     * returned. Never a candidate list.
     */
    @PostMapping("/resolve-contact")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolveContact(@RequestBody ResolveRequest req) {
        internalOnly();
        UUID tenantId = req.tenantId();
        String kind = req.kind() == null ? "" : req.kind().trim().toLowerCase(Locale.ROOT);
        String value = req.value() == null ? "" : req.value().trim();
        UUID healthId = null;
        if (tenantId != null && !value.isBlank()) {
            String hash = contactHasher.hash(kind, value);
            if (hash != null) {
                Optional<ClientEntity> match = switch (kind) {
                    case "phone" -> clientRepository.findByTenantIdAndPhoneHash(tenantId, hash);
                    case "email" -> clientRepository.findByTenantIdAndEmailHash(tenantId, hash);
                    default -> Optional.empty();
                };
                healthId = match.map(ClientEntity::getHealthId).orElse(null);
            }
        }
        return uniform(healthId);
    }

    /**
     * Start a record claim: issue a second-factor OTP to the contact recorded on
     * the (already privately-resolved) Health ID. Returns the OTP + raw contact
     * ONCE for internal delivery by the BFF; the client only sees maskedContact.
     */
    @PostMapping("/claim/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claimStart(@RequestBody ClaimStartRequest req) {
        internalOnly();
        var ch = recordClaimService.startClaim(req.tenantId(), req.healthId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("challengeId", ch.challengeId().toString());
        data.put("otp", ch.otp());                 // internal-only; BFF delivers, never to client
        data.put("contact", ch.contact());         // internal-only
        data.put("maskedContact", ch.maskedContact());
        data.put("channel", ch.channel());
        data.put("deliverable", ch.deliverable());
        return ResponseEntity.ok(ApiResponse.ok(data, null));
    }

    /**
     * Verify a record-claim OTP and, on success, bind the account to the Health ID
     * (RECORD_LINKED). Returns {@code verified} + the Health ID on success only.
     */
    @PostMapping("/claim/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claimVerify(@RequestBody ClaimVerifyRequest req) {
        internalOnly();
        var v = recordClaimService.verifyClaim(req.challengeId(), req.code(), req.accountRef());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("verified", v.verified());
        data.put("healthId", v.verified() && v.healthId() != null ? v.healthId().toString() : null);
        return ResponseEntity.ok(ApiResponse.ok(data, null));
    }

    private UUID parseHealthIdIfPresent(UUID tenantId, String value) {
        try {
            UUID hid = UUID.fromString(value);
            return clientRepository.findByTenantIdAndHealthId(tenantId, hid).isPresent() ? hid : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> uniform(UUID healthId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("healthId", healthId != null ? healthId.toString() : null);
        data.put("resolved", healthId != null);
        return ResponseEntity.ok(ApiResponse.ok(data, null));
    }

    private void internalOnly() {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "INTERNAL_ONLY");
        }
    }
}
