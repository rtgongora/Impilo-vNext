package zw.gov.mohcc.impilo.orgregistry.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgType;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.VerificationStatus;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WgvMirrorInventoryServiceTest {

    @Mock
    OrganizationRepository organizationRepository;

    private OrganizationEntity mirrorRow(String code, String sourceRef) {
        OrganizationEntity o = new OrganizationEntity();
        o.setId(UUID.randomUUID());
        o.setTenantId(UUID.randomUUID());
        o.setCode(code);
        o.setLegalName("Legal " + code);
        o.setOrgType(OrgType.GOVERNMENT_HEALTH_SERVICE);
        o.setStatus("ACTIVE");
        o.setVerificationStatus(VerificationStatus.VERIFIED);
        o.setSource("WGV_MIRROR");
        o.setSourceRef(sourceRef);
        return o;
    }

    @Test
    void inventory_mapsMirrorRowsToItems() {
        WgvMirrorInventoryService svc = new WgvMirrorInventoryService(organizationRepository);
        UUID srcA = UUID.randomUUID();
        OrganizationEntity a = mirrorRow("ORG-A", srcA.toString());
        Page<OrganizationEntity> page = new PageImpl<>(List.of(a), PageRequest.of(0, 200), 1);
        when(organizationRepository.findBySourceOrderByCreatedAtAsc(eq("WGV_MIRROR"), eq(PageRequest.of(0, 200))))
                .thenReturn(page);

        OrgRegistryDtos.MirrorInventoryPage result = svc.inventory(0, 200);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        OrgRegistryDtos.MirrorInventoryItem item = result.items().get(0);
        assertThat(item.orgRegistryId()).isEqualTo(a.getId());
        assertThat(item.sourceRef()).isEqualTo(srcA.toString());
        assertThat(item.code()).isEqualTo("ORG-A");
        assertThat(item.legalName()).isEqualTo("Legal ORG-A");
        assertThat(item.status()).isEqualTo("ACTIVE");
        assertThat(item.verificationStatus()).isEqualTo("VERIFIED");
    }

    @Test
    void inventory_emptyMirror_returnsEmptyPage_notCrash() {
        WgvMirrorInventoryService svc = new WgvMirrorInventoryService(organizationRepository);
        when(organizationRepository.findBySourceOrderByCreatedAtAsc(eq("WGV_MIRROR"), eq(PageRequest.of(0, 200))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        OrgRegistryDtos.MirrorInventoryPage result = svc.inventory(0, 200);

        assertThat(result.totalElements()).isZero();
        assertThat(result.items()).isEmpty();
    }
}
