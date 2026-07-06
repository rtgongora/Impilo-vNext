package zw.gov.mohcc.impilo.governance.reconcile;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import zw.gov.mohcc.impilo.governance.mirror.MirrorInventoryDtos;
import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorProperties;
import zw.gov.mohcc.impilo.governance.mirror.OrgRegistryMirrorClient;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgMirrorReconciliationServiceTest {

    @Mock
    OrganisationRepository organisationRepository;
    @Mock
    OrgRegistryMirrorClient mirrorClient;

    private OrganisationEntity wgvOrg(UUID id, String code, String legalName, String status) {
        OrganisationEntity o = new OrganisationEntity(id, UUID.randomUUID(), code, "Name-" + code, "HOSPITAL_GROUP", status);
        o.setLegalName(legalName);
        return o;
    }

    private MirrorInventoryDtos.InventoryRow mirrorRow(UUID wgvId, String code, String legalName, String status) {
        return new MirrorInventoryDtos.InventoryRow(
                UUID.randomUUID(), wgvId.toString(), code, legalName, status, "VERIFIED");
    }

    private OrgMirrorReconciliationService service() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setBackfillPageSize(50);
        return new OrgMirrorReconciliationService(organisationRepository, mirrorClient, props);
    }

    @Test
    void fullyMirrored_noDrift_reports100Pct() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(organisationRepository.findByActiveFlagTrueOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        wgvOrg(a, "ORG-A", "Alpha Ltd", "ACTIVE"),
                        wgvOrg(b, "ORG-B", "Beta Ltd", "active"))));
        when(mirrorClient.fetchInventory(any(), any())).thenReturn(List.of(
                mirrorRow(a, "ORG-A", "Alpha Ltd", "ACTIVE"),
                // status normalised (mirror stores upper-case) — must NOT be flagged as drift
                mirrorRow(b, "ORG-B", "Beta Ltd", "ACTIVE")));

        OrgMirrorReconciliationReport report = service().reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertThat(report.wgvActiveTotal()).isEqualTo(2);
        assertThat(report.mirroredCount()).isEqualTo(2);
        assertThat(report.missingSourceRefs()).isEmpty();
        assertThat(report.driftRows()).isEmpty();
        assertThat(report.completenessPct()).isEqualTo(100.0);
    }

    @Test
    void missingMirrorRow_lowersCompleteness_andListsSourceRef() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(organisationRepository.findByActiveFlagTrueOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        wgvOrg(a, "ORG-A", "Alpha Ltd", "ACTIVE"),
                        wgvOrg(b, "ORG-B", "Beta Ltd", "ACTIVE"),
                        wgvOrg(c, "ORG-C", "Gamma Ltd", "ACTIVE"))));
        when(mirrorClient.fetchInventory(any(), any())).thenReturn(List.of(
                mirrorRow(a, "ORG-A", "Alpha Ltd", "ACTIVE"),
                mirrorRow(b, "ORG-B", "Beta Ltd", "ACTIVE")));

        OrgMirrorReconciliationReport report = service().reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertThat(report.wgvActiveTotal()).isEqualTo(3);
        assertThat(report.mirroredCount()).isEqualTo(2);
        assertThat(report.missingSourceRefs()).containsExactly(c.toString());
        assertThat(report.driftRows()).isEmpty();
        assertThat(report.completenessPct()).isEqualTo(66.67);
    }

    @Test
    void driftInCodeLegalNameAndStatus_isReportedPerField() {
        UUID a = UUID.randomUUID();
        when(organisationRepository.findByActiveFlagTrueOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        wgvOrg(a, "ORG-A", "Alpha Ltd", "ACTIVE"))));
        when(mirrorClient.fetchInventory(any(), any())).thenReturn(List.of(
                mirrorRow(a, "ORG-A-OLD", "Alpha Limited", "SUSPENDED")));

        OrgMirrorReconciliationReport report = service().reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertThat(report.mirroredCount()).isEqualTo(1);
        assertThat(report.completenessPct()).isEqualTo(100.0);
        assertThat(report.driftRows()).hasSize(1);
        assertThat(report.driftRows().get(0).sourceRef()).isEqualTo(a.toString());
        assertThat(report.driftRows().get(0).driftFields()).containsExactlyInAnyOrder("code", "legalName", "status");
    }

    @Test
    void emptyMirror_reportsZeroPctHonestly_notCrash() {
        UUID a = UUID.randomUUID();
        when(organisationRepository.findByActiveFlagTrueOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        wgvOrg(a, "ORG-A", "Alpha Ltd", "ACTIVE"))));
        when(mirrorClient.fetchInventory(any(), any())).thenReturn(List.of());

        OrgMirrorReconciliationReport report = service().reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertThat(report.wgvActiveTotal()).isEqualTo(1);
        assertThat(report.mirroredCount()).isZero();
        assertThat(report.missingSourceRefs()).containsExactly(a.toString());
        assertThat(report.completenessPct()).isEqualTo(0.0);
    }
}
