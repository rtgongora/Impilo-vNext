package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.GdhcnReadinessAssessmentEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.GdhcnReadinessAssessmentRepository;
import zw.gov.mohcc.impilo.tshepo.authz.trust.GdhcnReadinessDomain;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;

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

/** Proves the B6 readiness cockpit backend: honest full-catalogue reads + dual-gated updates. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GdhcnReadinessServiceTest {

    @Mock private GdhcnReadinessAssessmentRepository repository;
    @Mock private StepUpService stepUpService;
    @Mock private AuditPublisher auditPublisher;

    private GdhcnReadinessService service;
    private final UUID tenantId = UUID.randomUUID();
    private static final List<String> ADMIN = List.of("TRUST_ADMIN");
    private static final List<String> NON_ADMIN = List.of("NURSE");

    @BeforeEach
    void setUp() {
        service = new GdhcnReadinessService(repository, stepUpService, auditPublisher, new AuthzProperties());
        when(repository.save(any())).thenAnswer(inv -> {
            GdhcnReadinessAssessmentEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void list_returnsFullCatalogue_withHonestDefaults() {
        when(repository.findByTenantId(tenantId)).thenReturn(List.of());
        List<GdhcnReadinessService.DomainReadiness> result = service.listReadiness(tenantId);

        assertThat(result).hasSize(GdhcnReadinessDomain.values().length);
        assertThat(result).allSatisfy(d -> {
            assertThat(d.currentMaturity()).isEqualTo(TrustReadiness.NOT_STARTED);
            assertThat(d.targetMaturity()).isEqualTo(TrustReadiness.PRODUCTION_NOT_READY);
        });
    }

    @Test
    void update_nonAdmin_failsClosed() {
        assertThatThrownBy(() -> service.updateDomain(tenantId, "nurse-1", NON_ADMIN, "GOVERNANCE",
                TrustReadiness.ASSESSMENT_BASELINE, null, null, null, null, null))
                .isInstanceOf(SecurityException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_adminButNoStepUp_failsClosed() {
        when(stepUpService.hasRecentStepUp(tenantId, "admin-1")).thenReturn(false);
        assertThatThrownBy(() -> service.updateDomain(tenantId, "admin-1", ADMIN, "GOVERNANCE",
                TrustReadiness.ASSESSMENT_BASELINE, null, null, null, null, null))
                .isInstanceOf(SecurityException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_withAdminAndStepUp_upserts() {
        when(stepUpService.hasRecentStepUp(tenantId, "admin-1")).thenReturn(true);
        when(repository.findByTenantIdAndDomainKey(tenantId, "GOVERNANCE")).thenReturn(Optional.empty());

        GdhcnReadinessService.DomainReadiness d = service.updateDomain(tenantId, "admin-1", ADMIN, "GOVERNANCE",
                TrustReadiness.FOUNDATION_IN_PROGRESS, TrustReadiness.PRODUCTION_NOT_READY,
                "Trust Office", "baseline gathered", "in progress", null);

        assertThat(d.currentMaturity()).isEqualTo(TrustReadiness.FOUNDATION_IN_PROGRESS);
        assertThat(d.owner()).isEqualTo("Trust Office");
        verify(auditPublisher).queueGovernanceEvent(eq("GDHCN_READINESS_UPDATED"), anyString(), any());
    }

    @Test
    void update_unknownDomain_isRejected() {
        when(stepUpService.hasRecentStepUp(tenantId, "admin-1")).thenReturn(true);
        assertThatThrownBy(() -> service.updateDomain(tenantId, "admin-1", ADMIN, "NOT_A_DOMAIN",
                TrustReadiness.ASSESSMENT_BASELINE, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
