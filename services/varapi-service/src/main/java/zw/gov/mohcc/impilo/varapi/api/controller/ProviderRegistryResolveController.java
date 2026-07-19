package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Private, server-side provider-identity resolution (D-P2), mirroring the
 * client-side {@code vito RegistryResolveController} pattern (Identity Journey
 * Doctrine §2). INTERNAL-only. Resolves a presented professional identifier to
 * a provider public id — or a uniform miss — and returns <b>nothing else</b>:
 * no candidate list, no names, no licence status, no existence signal beyond
 * the uniform envelope.
 *
 * <p>Kinds: {@code PROVIDER_ID} (provider public id) and {@code COUNCIL_REG}
 * (council registration number, optionally scoped by {@code councilCode}).
 * EC/employee numbers are deliberately NOT resolvable here — employment
 * matching is the workforce-governance WS-D lane, not a registry lookup.</p>
 *
 * <p>Anti-enumeration: uniform 200 response shape on hit and miss alike, plus a
 * {@value #TIMING_FLOOR_MS} ms timing floor applied in-controller (defence in
 * depth — BFF callers add their own floor around composition).</p>
 */
@RestController
@RequestMapping("/v1/registry")
public class ProviderRegistryResolveController {

    static final long TIMING_FLOOR_MS = 120;

    private final ProviderRepository providerRepository;
    private final ProviderCouncilRegistrationRecordRepository registrationRepository;

    public ProviderRegistryResolveController(ProviderRepository providerRepository,
                                             ProviderCouncilRegistrationRecordRepository registrationRepository) {
        this.providerRepository = providerRepository;
        this.registrationRepository = registrationRepository;
    }

    public record ResolveRequest(UUID tenantId, String kind, String value, String councilCode) {}

    /**
     * Resolve a professional identifier to a provider public id. Uniform 200
     * with {@code data.providerPublicId} null on miss — never a reason.
     */
    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolve(@RequestBody ResolveRequest req) {
        internalOnly();
        long start = System.nanoTime();
        UUID tenantId = req.tenantId();
        String kind = req.kind() == null ? "" : req.kind().trim().toUpperCase(Locale.ROOT);
        String value = req.value() == null ? "" : req.value().trim();
        String providerPublicId = null;
        if (tenantId != null && !value.isBlank()) {
            providerPublicId = switch (kind) {
                case "PROVIDER_ID" -> providerRepository
                        .findByProviderPublicIdAndTenantId(value.toUpperCase(Locale.ROOT), tenantId)
                        .map(ProviderEntity::getProviderPublicId)
                        .orElse(null);
                case "COUNCIL_REG" -> resolveCouncilRegistration(tenantId, value, req.councilCode());
                default -> null;
            };
        }
        applyTimingFloor(start);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("providerPublicId", providerPublicId);
        data.put("resolved", providerPublicId != null);
        return ResponseEntity.ok(ApiResponse.ok(data, null));
    }

    private String resolveCouncilRegistration(UUID tenantId, String registrationNumber, String councilCode) {
        Optional<ProviderCouncilRegistrationRecordEntity> record =
                (councilCode != null && !councilCode.isBlank())
                        ? registrationRepository
                            .findFirstByTenantIdAndCouncil_CouncilCodeIgnoreCaseAndRegistrationNumberIgnoreCase(
                                tenantId, councilCode.trim(), registrationNumber)
                        : registrationRepository.findFirstByTenantIdAndRegistrationNumberIgnoreCase(
                                tenantId, registrationNumber);
        return record.map(ProviderCouncilRegistrationRecordEntity::getProvider)
                .map(ProviderEntity::getProviderPublicId)
                .orElse(null);
    }

    /** Constant-floor response time so a hit is not distinguishable from a miss. */
    private static void applyTimingFloor(long startNanos) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        long remaining = TIMING_FLOOR_MS - elapsedMs;
        if (remaining > 0) {
            try {
                Thread.sleep(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void internalOnly() {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "INTERNAL_ONLY");
        }
    }
}
