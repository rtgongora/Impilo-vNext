package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustAuthorityEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustDocumentSignerCertEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustIssuerSystemEntity;
import zw.gov.mohcc.impilo.tshepo.authz.service.TrustAuthorityService;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustEnvironment;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API for the Tshepo Trust Authority registry (Wave B B5). Reads are open to any
 * authenticated caller in the tenant; mutations are gated in the service by an admin role
 * (from the JWT realm roles) AND a recent step-up.
 */
@RestController
@RequestMapping("/v1/trust-authorities")
public class TrustAuthorityController {

    private final TrustAuthorityService service;

    public TrustAuthorityController(TrustAuthorityService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TrustAuthorityEntity> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @Valid @RequestBody CreateAuthorityRequest body) {
        TrustAuthorityEntity created = service.createAuthority(
                tenantId, actorId, realmRoles(jwt), body.name(), body.trustDomain(), body.environment());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<TrustAuthorityEntity> list(@RequestHeader("x-tenant-id") UUID tenantId) {
        return service.listAuthorities(tenantId);
    }

    @GetMapping("/{id}")
    public TrustAuthorityEntity get(@RequestHeader("x-tenant-id") UUID tenantId, @PathVariable UUID id) {
        return service.getAuthority(tenantId, id);
    }

    @PostMapping("/{id}/status")
    public TrustAuthorityEntity transition(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @PathVariable UUID id,
            @Valid @RequestBody StatusRequest body) {
        return service.transitionStatus(tenantId, actorId, realmRoles(jwt), id, body.status());
    }

    @PostMapping("/{id}/readiness")
    public TrustAuthorityEntity readiness(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @PathVariable UUID id,
            @Valid @RequestBody ReadinessRequest body) {
        return service.updateReadiness(tenantId, actorId, realmRoles(jwt), id, body.readiness());
    }

    @GetMapping("/{id}/issuers")
    public List<TrustIssuerSystemEntity> listIssuers(
            @RequestHeader("x-tenant-id") UUID tenantId, @PathVariable UUID id) {
        return service.listIssuers(tenantId, id);
    }

    @PostMapping("/{id}/issuers")
    public ResponseEntity<TrustIssuerSystemEntity> addIssuer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @PathVariable UUID id,
            @Valid @RequestBody IssuerRequest body) {
        TrustIssuerSystemEntity created = service.addIssuer(
                tenantId, actorId, realmRoles(jwt), id, body.name(), body.issuerUri());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}/document-signers")
    public List<TrustDocumentSignerCertEntity> listDocumentSigners(
            @RequestHeader("x-tenant-id") UUID tenantId, @PathVariable UUID id) {
        return service.listDocumentSigners(tenantId, id);
    }

    @PostMapping("/{id}/document-signers")
    public ResponseEntity<TrustDocumentSignerCertEntity> addDocumentSigner(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @PathVariable UUID id,
            @Valid @RequestBody DocumentSignerRequest body) {
        TrustDocumentSignerCertEntity created = service.addDocumentSigner(
                tenantId, actorId, realmRoles(jwt), id, body.kid(), body.subject(),
                body.notBefore(), body.notAfter());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
        if (realm == null || !(realm.get("roles") instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return out;
    }

    public record CreateAuthorityRequest(
            @NotBlank String name,
            @NotBlank String trustDomain,
            @NotNull TrustEnvironment environment) {}

    public record StatusRequest(@NotNull TrustStatus status) {}

    public record ReadinessRequest(@NotNull TrustReadiness readiness) {}

    public record IssuerRequest(@NotBlank String name, @NotBlank String issuerUri) {}

    public record DocumentSignerRequest(
            @NotBlank String kid,
            @NotBlank String subject,
            Instant notBefore,
            Instant notAfter) {}
}
