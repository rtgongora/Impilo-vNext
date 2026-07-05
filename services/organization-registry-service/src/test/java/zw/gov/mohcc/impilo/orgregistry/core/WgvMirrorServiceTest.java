package zw.gov.mohcc.impilo.orgregistry.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgType;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.VerificationStatus;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WgvMirrorServiceTest {

    @Mock
    OrganizationRepository organizationRepository;
    @Mock
    OrgRegistryOutboxWriter outboxWriter;

    WgvMirrorService service;

    UUID tenant;

    @BeforeEach
    void setUp() {
        service = new WgvMirrorService(organizationRepository, outboxWriter);
        tenant = UUID.randomUUID();
    }

    @Test
    void mirror_sameSourceRefTwice_upsertsSingleRow() throws Exception {
        UUID wgvOrgId = UUID.randomUUID();
        OrgRegistryDtos.WgvOrganisationPayload payload = new OrgRegistryDtos.WgvOrganisationPayload(
                wgvOrgId, "MOHCC-HQ", "Ministry of Health and Child Care", "MOHCC", "active", null);

        // Simulate persistence: first lookup misses, second lookup returns the stored row.
        AtomicReference<OrganizationEntity> stored = new AtomicReference<>();
        when(organizationRepository.findBySourceAndSourceRef(eq("WGV_MIRROR"), eq(wgvOrgId.toString())))
                .thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(organizationRepository.save(any(OrganizationEntity.class))).thenAnswer(inv -> {
            OrganizationEntity o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            stored.set(o);
            return o;
        });

        WgvMirrorService.MirrorResult first = service.mirror(tenant, payload);
        assertThat(first.created()).isTrue();
        assertThat(first.organization().getSource()).isEqualTo("WGV_MIRROR");
        assertThat(first.organization().getSourceRef()).isEqualTo(wgvOrgId.toString());
        assertThat(first.organization().getOrgType()).isEqualTo(OrgType.GOVERNMENT_HEALTH_SERVICE);
        assertThat(first.organization().getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);

        OrgRegistryDtos.WgvOrganisationPayload updated = new OrgRegistryDtos.WgvOrganisationPayload(
                wgvOrgId, "MOHCC-HQ", "Ministry of Health & Child Care", "MOHCC", "active", null);
        WgvMirrorService.MirrorResult second = service.mirror(tenant, updated);

        assertThat(second.created()).isFalse();
        assertThat(second.organization().getId()).isEqualTo(first.organization().getId());
        assertThat(second.organization().getLegalName()).isEqualTo("Ministry of Health & Child Care");
        verify(organizationRepository, times(2)).save(any(OrganizationEntity.class));
        assertThat(stored.get().getId()).isEqualTo(first.organization().getId());
    }

    @Test
    void mirror_mapsUnknownOrganisationTypeToOther() throws Exception {
        UUID wgvOrgId = UUID.randomUUID();
        when(organizationRepository.findBySourceAndSourceRef(eq("WGV_MIRROR"), eq(wgvOrgId.toString())))
                .thenReturn(Optional.empty());
        when(organizationRepository.save(any(OrganizationEntity.class))).thenAnswer(inv -> {
            OrganizationEntity o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        WgvMirrorService.MirrorResult result = service.mirror(tenant,
                new OrgRegistryDtos.WgvOrganisationPayload(
                        wgvOrgId, "X-1", "Some Body", "SOMETHING_ELSE", "suspended", UUID.randomUUID()));

        assertThat(result.organization().getOrgType()).isEqualTo(OrgType.OTHER);
        assertThat(result.organization().getWgvOrgTypeRaw()).isEqualTo("SOMETHING_ELSE");
        assertThat(result.organization().getStatus()).isEqualTo("SUSPENDED");
        assertThat(result.organization().getWgvParentSourceRef()).isNotNull();
    }

    @Test
    void mirror_requiresWgvOrganisationId() {
        assertThatThrownBy(() -> service.mirror(tenant,
                new OrgRegistryDtos.WgvOrganisationPayload(
                        null, "X-1", "Some Body", "NGO", "active", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }
}
