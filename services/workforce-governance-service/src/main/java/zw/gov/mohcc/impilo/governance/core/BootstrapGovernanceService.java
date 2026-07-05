package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BootstrapGovernanceService {

    private final BootstrapStateRepository bootstrapStateRepository;
    private final BootstrapAccountRepository bootstrapAccountRepository;
    private final OrganisationRepository organisationRepository;
    private final CountryOperationRepository countryOperationRepository;
    private final ObjectMapper objectMapper;

    public BootstrapGovernanceService(BootstrapStateRepository bootstrapStateRepository,
                                      BootstrapAccountRepository bootstrapAccountRepository,
                                      OrganisationRepository organisationRepository,
                                      CountryOperationRepository countryOperationRepository,
                                      ObjectMapper objectMapper) {
        this.bootstrapStateRepository = bootstrapStateRepository;
        this.bootstrapAccountRepository = bootstrapAccountRepository;
        this.organisationRepository = organisationRepository;
        this.countryOperationRepository = countryOperationRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> state(UUID tenantId) {
        BootstrapStateEntity entity = bootstrapStateRepository.findByTenantId(tenantId).orElseGet(() -> init(tenantId));
        boolean activeNationalAdminExists = entity.getActiveNationalAdminUserId() != null && !entity.getActiveNationalAdminUserId().isBlank();
        return Map.of(
                "bootstrapOpen", entity.isBootstrapOpen(),
                "bootstrapClosedAt", entity.getBootstrapClosedAt() != null ? entity.getBootstrapClosedAt().toString() : null,
                "activeNationalAdminExists", activeNationalAdminExists,
                "recoveryMode", entity.isRecoveryMode()
        );
    }

    /**
     * Read-through only (IATG WS-A): the tenant's bootstrap state alongside its
     * country operation, without initialising or mutating anything. Existing
     * bootstrap behavior ({@link #state(UUID)} and lifecycle methods) is unchanged.
     */
    public Map<String, Object> stateWithCountryOperation(UUID tenantId) {
        Map<String, Object> out = new LinkedHashMap<>();
        BootstrapStateEntity entity = bootstrapStateRepository.findByTenantId(tenantId).orElse(null);
        if (entity != null) {
            out.put("bootstrapOpen", entity.isBootstrapOpen());
            out.put("bootstrapClosedAt",
                    entity.getBootstrapClosedAt() != null ? entity.getBootstrapClosedAt().toString() : null);
            out.put("activeNationalAdminExists",
                    entity.getActiveNationalAdminUserId() != null && !entity.getActiveNationalAdminUserId().isBlank());
            out.put("recoveryMode", entity.isRecoveryMode());
        } else {
            out.put("bootstrapOpen", true);
            out.put("bootstrapClosedAt", null);
            out.put("activeNationalAdminExists", false);
            out.put("recoveryMode", false);
        }
        out.put("countryOperation", countryOperationRepository.findByTenantId(tenantId)
                .map(op -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("id", op.getId().toString());
                    summary.put("isoCountryCode", op.getIsoCountryCode());
                    summary.put("displayName", op.getDisplayName());
                    summary.put("status", op.getStatus());
                    summary.put("sovereignOrganisationId",
                            op.getSovereignOrganisationId() != null ? op.getSovereignOrganisationId().toString() : null);
                    return (Object) summary;
                })
                .orElse(null));
        return out;
    }

    @Transactional
    public BootstrapAccountEntity createAccount(UUID tenantId, Map<String, Object> request) {
        BootstrapAccountEntity account = new BootstrapAccountEntity();
        account.init(tenantId,
                str(request.get("bootstrapMethod")),
                str(request.get("nominatedEmail")),
                str(request.get("nominatedFullName")),
                parseUuid(request.get("sovereignOrganisationId")));
        account.setApprovalReference(str(request.get("approvalReference")));
        if (request.get("bootstrapAccountId") != null) {
            account.overrideId(UUID.fromString(request.get("bootstrapAccountId").toString()));
        }
        return bootstrapAccountRepository.save(account);
    }

    @Transactional
    public BootstrapAccountEntity activate(UUID accountId, Map<String, Object> request) {
        BootstrapAccountEntity account = bootstrapAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Bootstrap account not found"));
        account.setStatus("ACTIVE");
        account.setKeycloakUserId(str(request.get("keycloakUserId")).isBlank() ? account.getNominatedEmail() : str(request.get("keycloakUserId")));
        account.setActivatedAt(Instant.now());
        final BootstrapAccountEntity saved = bootstrapAccountRepository.save(account);
        final UUID tenantId = saved.getTenantId();
        BootstrapStateEntity state = bootstrapStateRepository.findByTenantId(tenantId).orElseGet(() -> init(tenantId));
        state.setBootstrapOpen(false);
        state.setBootstrapClosedAt(Instant.now());
        state.setActiveNationalAdminUserId(saved.getKeycloakUserId());
        state.setBootstrapAccountId(saved.getId());
        bootstrapStateRepository.save(state);
        return saved;
    }

    @Transactional
    public BootstrapStateEntity close(UUID tenantId) {
        BootstrapStateEntity state = bootstrapStateRepository.findByTenantId(tenantId).orElseGet(() -> init(tenantId));
        state.setBootstrapOpen(false);
        state.setBootstrapClosedAt(Instant.now());
        return bootstrapStateRepository.save(state);
    }

    @Transactional
    public BootstrapStateEntity openRecovery(UUID tenantId, String ceremonyReference) {
        BootstrapStateEntity state = bootstrapStateRepository.findByTenantId(tenantId).orElseGet(() -> init(tenantId));
        state.setRecoveryMode(true);
        state.setRecoveryOpenedAt(Instant.now());
        state.setBootstrapOpen(true);
        state.setMetadata(writeMeta(Map.of("ceremonyReference", ceremonyReference)));
        return bootstrapStateRepository.save(state);
    }

    @Transactional
    public BootstrapStateEntity closeRecovery(UUID tenantId) {
        BootstrapStateEntity state = bootstrapStateRepository.findByTenantId(tenantId).orElseGet(() -> init(tenantId));
        state.setRecoveryMode(false);
        state.setRecoveryClosedAt(Instant.now());
        state.setBootstrapOpen(false);
        return bootstrapStateRepository.save(state);
    }

    private BootstrapStateEntity init(UUID tenantId) {
        BootstrapStateEntity entity = new BootstrapStateEntity();
        entity.init(tenantId);
        return bootstrapStateRepository.save(entity);
    }

    private String writeMeta(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static UUID parseUuid(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return UUID.fromString(value.toString());
    }
}
