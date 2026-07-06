package zw.gov.mohcc.impilo.governance.orgkey;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import zw.gov.mohcc.impilo.governance.mirror.MirrorInventoryDtos;
import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorProperties;
import zw.gov.mohcc.impilo.governance.mirror.OrgRegistryMirrorClient;
import zw.gov.mohcc.impilo.governance.persistence.FacilityOrganisationLinkEntity;
import zw.gov.mohcc.impilo.governance.persistence.FacilityOrganisationLinkRepository;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentEntity;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentRepository;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationMembershipRepository;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationUnitRepository;
import zw.gov.mohcc.impilo.governance.persistence.SiteOrganisationLinkRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgKeyBackfillServiceTest {

    @Mock HscEmploymentRepository hscEmploymentRepository;
    @Mock FacilityOrganisationLinkRepository facilityOrganisationLinkRepository;
    @Mock SiteOrganisationLinkRepository siteOrganisationLinkRepository;
    @Mock OrganisationUnitRepository organisationUnitRepository;
    @Mock OrganisationMembershipRepository organisationMembershipRepository;
    @Mock OrgRegistryMirrorClient mirrorClient;

    private OrgKeyBackfillService service(OrgMirrorProperties props) {
        return new OrgKeyBackfillService(props, mirrorClient,
                hscEmploymentRepository, facilityOrganisationLinkRepository, siteOrganisationLinkRepository,
                organisationUnitRepository, organisationMembershipRepository);
    }

    @Test
    void enabled_populatedMirror_setsDualKeyViaSourceRef() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setEnabled(true);
        UUID tenant = UUID.randomUUID();
        UUID wgvOrgId = UUID.randomUUID();
        UUID orgRegistryId = UUID.randomUUID();

        HscEmploymentEntity hsc = new HscEmploymentEntity();
        hsc.init(tenant);
        hsc.setEmployerOrganisationId(wgvOrgId);

        FacilityOrganisationLinkEntity link = new FacilityOrganisationLinkEntity(
                UUID.randomUUID(), tenant, 1L, wgvOrgId, "OWNER", "ACTIVE");

        when(mirrorClient.fetchInventory(any(), any())).thenReturn(List.of(
                new MirrorInventoryDtos.InventoryRow(orgRegistryId, wgvOrgId.toString(),
                        "ORG-A", "Alpha Ltd", "ACTIVE", "VERIFIED")));

        when(hscEmploymentRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of(hsc));
        when(facilityOrganisationLinkRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of(link));
        when(siteOrganisationLinkRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of());
        when(organisationUnitRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of());
        when(organisationMembershipRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of());

        OrgKeyBackfillService.OrgKeyBackfillResult result = service(props).backfill(tenant, UUID.randomUUID());

        assertThat(result.enabled()).isTrue();
        assertThat(result.mappedRefs()).isEqualTo(1);
        assertThat(result.updatedByTable().get("wgv_hsc_employment")).isEqualTo(1L);
        assertThat(result.updatedByTable().get("wgv_facility_organisation_link")).isEqualTo(1L);
        assertThat(hsc.getOrgRegistryOrgId()).isEqualTo(orgRegistryId);
        assertThat(link.getOrgRegistryOrgId()).isEqualTo(orgRegistryId);
        verify(hscEmploymentRepository).saveAll(List.of(hsc));
        verify(facilityOrganisationLinkRepository).saveAll(List.of(link));
    }

    @Test
    void enabled_rowWithNoMatchingSourceRef_isLeftNull() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setEnabled(true);
        UUID tenant = UUID.randomUUID();
        UUID wgvOrgId = UUID.randomUUID();

        HscEmploymentEntity hsc = new HscEmploymentEntity();
        hsc.init(tenant);
        hsc.setEmployerOrganisationId(wgvOrgId);

        // Mirror does not contain this wgv org id.
        when(mirrorClient.fetchInventory(any(), any())).thenReturn(List.of(
                new MirrorInventoryDtos.InventoryRow(UUID.randomUUID(), UUID.randomUUID().toString(),
                        "OTHER", "Other Ltd", "ACTIVE", "VERIFIED")));
        when(hscEmploymentRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of(hsc));
        when(facilityOrganisationLinkRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of());
        when(siteOrganisationLinkRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of());
        when(organisationUnitRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of());
        when(organisationMembershipRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenant)).thenReturn(List.of());

        OrgKeyBackfillService.OrgKeyBackfillResult result = service(props).backfill(tenant, UUID.randomUUID());

        assertThat(result.updatedByTable().get("wgv_hsc_employment")).isZero();
        assertThat(hsc.getOrgRegistryOrgId()).isNull();
        verify(hscEmploymentRepository, never()).saveAll(any());
    }

    @Test
    void disabled_isNoOp() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setEnabled(false);
        lenient().when(mirrorClient.fetchInventory(any(), any())).thenReturn(List.of());

        OrgKeyBackfillService.OrgKeyBackfillResult result = service(props).backfill(UUID.randomUUID(), UUID.randomUUID());

        assertThat(result.enabled()).isFalse();
        assertThat(result.mappedRefs()).isZero();
        assertThat(result.updatedByTable()).isEmpty();
        verifyNoInteractions(hscEmploymentRepository, facilityOrganisationLinkRepository,
                siteOrganisationLinkRepository, organisationUnitRepository, organisationMembershipRepository);
        verifyNoInteractions(mirrorClient);
    }
}
