package zw.gov.mohcc.impilo.governance.mirror;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import zw.gov.mohcc.impilo.governance.persistence.OrganisationEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgMirrorBackfillServiceTest {

    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private OrgRegistryMirrorClient mirrorClient;

    private OrganisationEntity org(String code) {
        return new OrganisationEntity(UUID.randomUUID(), UUID.randomUUID(), code, "N-" + code, "HOSPITAL_GROUP", "ACTIVE");
    }

    @Test
    void backfill_pushesEveryActiveRow_andReportsCounts() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setEnabled(true);
        props.setBackfillPageSize(2);
        OrgMirrorBackfillService svc = new OrgMirrorBackfillService(organisationRepository, mirrorClient, props);

        List<OrganisationEntity> orgs = List.of(org("A"), org("B"), org("C"));
        // Page 0: A, B (hasNext=true); page 1: C (hasNext=false)
        when(organisationRepository.findByActiveFlagTrueOrderByCreatedAtAsc(PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(orgs.subList(0, 2), PageRequest.of(0, 2), 3));
        when(organisationRepository.findByActiveFlagTrueOrderByCreatedAtAsc(PageRequest.of(1, 2)))
                .thenReturn(new PageImpl<>(orgs.subList(2, 3), PageRequest.of(1, 2), 3));
        // A,C succeed; B fails
        when(mirrorClient.push(any(), any(), any())).thenReturn(true, false, true);

        OrgMirrorBackfillService.BackfillResult result = svc.backfill(UUID.randomUUID());

        assertTrue(result.enabled());
        assertEquals(3, result.total());
        assertEquals(2, result.pushed());
        assertEquals(1, result.failed());
        verify(mirrorClient, times(3)).push(any(), any(), any());
    }

    @Test
    void backfill_reRunnable_idempotentPushPerRowUsingRowTenant() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setEnabled(true);
        OrgMirrorBackfillService svc = new OrgMirrorBackfillService(organisationRepository, mirrorClient, props);

        OrganisationEntity a = org("A");
        when(organisationRepository.findByActiveFlagTrueOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a)));
        when(mirrorClient.push(any(), any(), any())).thenReturn(true);

        svc.backfill(null);
        svc.backfill(null);

        // Re-runnable: two sweeps push the same row twice (receiver is idempotent),
        // each carrying the row's own tenant id.
        verify(mirrorClient, times(2)).push(any(), any(), any());
    }

    @Test
    void flagOff_backfillIsNoOp() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setEnabled(false);
        OrgMirrorBackfillService svc = new OrgMirrorBackfillService(organisationRepository, mirrorClient, props);

        OrgMirrorBackfillService.BackfillResult result = svc.backfill(UUID.randomUUID());

        assertFalse(result.enabled());
        assertEquals(0, result.total());
        assertEquals(0, result.pushed());
        verifyNoInteractions(organisationRepository);
        verifyNoInteractions(mirrorClient);
    }
}
