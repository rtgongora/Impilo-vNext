package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.indawo.domain.SiteEntity;
import zw.gov.mohcc.impilo.indawo.domain.SiteLifecycleStatus;
import zw.gov.mohcc.impilo.indawo.domain.SiteOperatorGrantEntity;
import zw.gov.mohcc.impilo.indawo.domain.SiteRegistrationCaseEntity;
import zw.gov.mohcc.impilo.indawo.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.indawo.repository.SiteOperatorGrantRepository;
import zw.gov.mohcc.impilo.indawo.repository.SiteRegistrationCaseRepository;
import zw.gov.mohcc.impilo.indawo.repository.SiteRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SJ1/SJ2 mirrors of the Tuso doctrine tests: evidence-mandatory role-scoped
 * grants with enforced expiry; registration receipts identical on both paths;
 * silent block creates no site; provisional sites are DRAFT.
 */
@ExtendWith(MockitoExtension.class)
class SiteSelfServiceServiceTest {

    @Mock private SiteRepository siteRepository;
    @Mock private SiteOperatorGrantRepository grantRepository;
    @Mock private SiteRegistrationCaseRepository caseRepository;
    @Mock private OutboxEventRepository outboxRepository;

    private SiteSelfServiceService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SiteSelfServiceService(siteRepository, grantRepository, caseRepository,
                outboxRepository, new ObjectMapper());
        RequestContextHolder.set(RequestContext.of(
                tenantId.toString(), "national-spine", null, null, null, null, null));
        lenient().when(grantRepository.save(any())).thenAnswer(inv -> {
            SiteOperatorGrantEntity g = inv.getArgument(0);
            return g;
        });
        lenient().when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(siteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    private SiteEntity site() {
        SiteEntity site = mock(SiteEntity.class);
        lenient().when(site.getSiteId()).thenReturn(siteId);
        lenient().when(site.getTenantId()).thenReturn(tenantId);
        return site;
    }

    @Test
    void grantClaimRequiresEvidenceAndClosedRoleVocabulary() {
        SiteEntity existing = site();
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.submitGrantClaim(siteId,
                new SiteSelfServiceService.SubmitGrantClaim(
                        "HID-1", "SITE_OPERATOR", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceRef");

        assertThatThrownBy(() -> service.submitGrantClaim(siteId,
                new SiteSelfServiceService.SubmitGrantClaim(
                        "HID-1", "SUPREME_OPERATOR", null, "doc://e/1", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role");
        verify(grantRepository, never()).save(any());
    }

    @Test
    void grantClaimLandsPendingAndPublishes() {
        SiteEntity existing = site();
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(existing));

        SiteOperatorGrantEntity grant = service.submitGrantClaim(siteId,
                new SiteSelfServiceService.SubmitGrantClaim(
                        "HID-1", "site_operator", null, "doc://e/1", null,
                        LocalDate.of(2026, 12, 31), null));

        assertThat(grant.getApprovalState()).isEqualTo(SiteOperatorGrantEntity.STATE_PENDING);
        assertThat(grant.getRole()).isEqualTo("SITE_OPERATOR");
        verify(outboxRepository).save(any());
    }

    @Test
    void lapsedActiveGrantsExpire() {
        SiteOperatorGrantEntity lapsed = new SiteOperatorGrantEntity();
        lapsed.setApprovalState(SiteOperatorGrantEntity.STATE_ACTIVE);
        lapsed.setValidTo(LocalDate.of(2026, 6, 30));
        when(grantRepository.findByApprovalStateAndValidToBefore(
                SiteOperatorGrantEntity.STATE_ACTIVE, LocalDate.of(2026, 7, 19)))
                .thenReturn(List.of(lapsed));

        assertThat(service.expireLapsedGrants(LocalDate.of(2026, 7, 19))).isEqualTo(1);
        assertThat(lapsed.getApprovalState()).isEqualTo(SiteOperatorGrantEntity.STATE_EXPIRED);
    }

    @Test
    void registrationReceiptIsIdenticalOnBothPaths_andSilentBlockCreatesNoSite() {
        // Path 1: no match → provisional DRAFT site.
        when(siteRepository.findByNormalisedName(any(), any(), any(), any())).thenReturn(List.of());
        SiteSelfServiceService.RegistrationReceipt provisional = service.registerSite(
                new SiteSelfServiceService.RegisterSiteRequest(
                        "HID-1", "Mbare Market", "MARKET", "MARKET", "Harare", "Harare",
                        null, "doc://permit/1", null));

        ArgumentCaptor<SiteEntity> siteCaptor = ArgumentCaptor.forClass(SiteEntity.class);
        verify(siteRepository).save(siteCaptor.capture());
        assertThat(siteCaptor.getValue().getLifecycleStatus())
                .isEqualTo(SiteLifecycleStatus.DRAFT.name());

        // Path 2: credible match → silent block, no further site writes.
        SiteEntity existing = site();
        when(siteRepository.findByNormalisedName(any(), any(), any(), any()))
                .thenReturn(List.of(existing));
        SiteSelfServiceService.RegistrationReceipt blocked = service.registerSite(
                new SiteSelfServiceService.RegisterSiteRequest(
                        "HID-2", "Mbare Market", "MARKET", "MARKET", "Harare", "Harare",
                        null, "doc://permit/2", null));

        verify(siteRepository, org.mockito.Mockito.times(1)).save(any());

        assertThat(blocked.status()).isEqualTo(provisional.status());
        assertThat(blocked.message()).isEqualTo(provisional.message());
        assertThat(blocked.message()).doesNotContain("duplicate", "match", "exists");

        ArgumentCaptor<SiteRegistrationCaseEntity> caseCaptor =
                ArgumentCaptor.forClass(SiteRegistrationCaseEntity.class);
        verify(caseRepository, org.mockito.Mockito.times(2)).save(caseCaptor.capture());
        assertThat(caseCaptor.getAllValues().get(1).getOutcome())
                .isEqualTo(SiteRegistrationCaseEntity.OUTCOME_DUPLICATE_REVIEW);
        assertThat(caseCaptor.getAllValues().get(1).getMatchContext()).contains("NAME_CATEGORY_DISTRICT");
    }
}
