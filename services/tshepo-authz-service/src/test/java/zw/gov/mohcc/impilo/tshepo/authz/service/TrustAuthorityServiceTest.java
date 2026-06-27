package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustAuthorityEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.TrustAuthorityRepository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.TrustDocumentSignerCertRepository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.TrustIssuerSystemRepository;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustEnvironment;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Proves the B5 registry's dual gate (admin role + recent step-up) and status-transition rules. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrustAuthorityServiceTest {

    @Mock private TrustAuthorityRepository authorityRepository;
    @Mock private TrustIssuerSystemRepository issuerRepository;
    @Mock private TrustDocumentSignerCertRepository certRepository;
    @Mock private StepUpService stepUpService;
    @Mock private AuditPublisher auditPublisher;

    private TrustAuthorityService service;

    private final UUID tenantId = UUID.randomUUID();
    private static final List<String> ADMIN = List.of("TRUST_ADMIN");
    private static final List<String> NON_ADMIN = List.of("NURSE");

    @BeforeEach
    void setUp() {
        service = new TrustAuthorityService(authorityRepository, issuerRepository, certRepository,
                stepUpService, auditPublisher, new AuthzProperties());
        when(authorityRepository.save(any())).thenAnswer(inv -> {
            TrustAuthorityEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        when(issuerRepository.save(any())).thenAnswer(inv -> {
            var e = (zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustIssuerSystemEntity) inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void create_withAdminAndStepUp_createsDraft() {
        when(stepUpService.hasRecentStepUp(tenantId, "admin-1")).thenReturn(true);

        TrustAuthorityEntity result = service.createAuthority(
                tenantId, "admin-1", ADMIN, "ZW National Trust", "health.zw", TrustEnvironment.UAT);

        assertThat(result.getStatus()).isEqualTo(TrustStatus.DRAFT);
        assertThat(result.getReadiness()).isEqualTo(TrustReadiness.NOT_STARTED);
        assertThat(result.getCreatedBy()).isEqualTo("admin-1");
        verify(auditPublisher).queueGovernanceEvent(eq("TRUST_AUTHORITY_CREATED"), anyString(), any());
    }

    @Test
    void create_nonAdmin_failsClosed_andDoesNotPersist() {
        assertThatThrownBy(() -> service.createAuthority(
                tenantId, "nurse-1", NON_ADMIN, "x", "y", TrustEnvironment.TEST))
                .isInstanceOf(SecurityException.class);
        verify(authorityRepository, never()).save(any());
    }

    @Test
    void create_adminButNoStepUp_failsClosed() {
        when(stepUpService.hasRecentStepUp(tenantId, "admin-1")).thenReturn(false);
        assertThatThrownBy(() -> service.createAuthority(
                tenantId, "admin-1", ADMIN, "x", "y", TrustEnvironment.TEST))
                .isInstanceOf(SecurityException.class);
        verify(authorityRepository, never()).save(any());
    }

    @Test
    void transition_legal_succeeds() {
        when(stepUpService.hasRecentStepUp(tenantId, "admin-1")).thenReturn(true);
        UUID id = UUID.randomUUID();
        TrustAuthorityEntity existing = draft(id);
        when(authorityRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        TrustAuthorityEntity result = service.transitionStatus(tenantId, "admin-1", ADMIN, id, TrustStatus.PENDING);
        assertThat(result.getStatus()).isEqualTo(TrustStatus.PENDING);
    }

    @Test
    void transition_illegal_isRejected() {
        when(stepUpService.hasRecentStepUp(tenantId, "admin-1")).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(authorityRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(draft(id)));

        // DRAFT cannot jump straight to ACTIVE.
        assertThatThrownBy(() -> service.transitionStatus(tenantId, "admin-1", ADMIN, id, TrustStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void addIssuer_nonAdmin_failsClosed() {
        assertThatThrownBy(() -> service.addIssuer(
                tenantId, "nurse-1", NON_ADMIN, UUID.randomUUID(), "n", "https://issuer"))
                .isInstanceOf(SecurityException.class);
        verify(issuerRepository, never()).save(any());
    }

    private TrustAuthorityEntity draft(UUID id) {
        TrustAuthorityEntity e = new TrustAuthorityEntity();
        e.setId(id);
        e.setTenantId(tenantId);
        e.setName("a");
        e.setTrustDomain("d");
        e.setEnvironment(TrustEnvironment.TEST);
        e.setStatus(TrustStatus.DRAFT);
        e.setReadiness(TrustReadiness.NOT_STARTED);
        e.setCreatedBy("admin-1");
        return e;
    }
}
