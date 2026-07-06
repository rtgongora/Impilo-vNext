package zw.gov.mohcc.impilo.orgregistry.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AffiliationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AffiliationRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Facility-admin affiliation recording (IATG Wave 2, WS-E). Proves that when a facility administrator
 * appointment goes ACTIVE in TUSO, the administering relationship is recorded as an ordinary
 * {@code FACILITY} affiliation via the existing {@link AffiliationService} (no new schema) and an
 * {@code affiliation:created} outbox event is emitted.
 */
@ExtendWith(MockitoExtension.class)
class FacilityAdminAffiliationServiceTest {

    @Mock AffiliationRepository affiliationRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock OrgRegistryOutboxWriter outboxWriter;

    AffiliationService service;

    UUID tenant;
    UUID orgId;
    final String facilityUuid = "11111111-2222-3333-4444-555555555555";

    @BeforeEach
    void setUp() {
        service = new AffiliationService(affiliationRepository, organizationRepository, outboxWriter);
        tenant = UUID.randomUUID();
        orgId = UUID.randomUUID();
    }

    private OrganizationEntity org() {
        OrganizationEntity o = new OrganizationEntity();
        o.setId(orgId);
        o.setTenantId(tenant);
        return o;
    }

    @Test
    void recordFacilityAdminAffiliation_createsActiveFacilityAffiliation_andEmitsOutbox() throws Exception {
        when(organizationRepository.findByTenantIdAndId(tenant, orgId)).thenReturn(Optional.of(org()));
        when(affiliationRepository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(affiliationRepository.save(any(AffiliationEntity.class))).thenAnswer(inv -> {
            AffiliationEntity a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });

        AffiliationEntity affiliation = service.recordFacilityAdminAffiliation(tenant, orgId,
                new OrgRegistryDtos.RecordFacilityAdminAffiliationRequest(
                        facilityUuid, "HID-100", "national-admin-1", null));

        ArgumentCaptor<AffiliationEntity> captor = ArgumentCaptor.forClass(AffiliationEntity.class);
        verify(affiliationRepository).save(captor.capture());
        AffiliationEntity saved = captor.getValue();
        assertThat(saved.getSubjectType()).isEqualTo("FACILITY");
        assertThat(saved.getSubjectRef()).isEqualTo(facilityUuid);
        assertThat(saved.getAffiliationType()).isEqualTo("FACILITY_ADMINISTRATION");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getSourceChannel()).isEqualTo("FACILITY_ADMIN_APPOINTMENT");
        assertThat(affiliation.getStatus()).isEqualTo("ACTIVE");

        // affiliation:created outbox event emitted.
        verify(outboxWriter).publish(any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq("affiliation"),
                org.mockito.ArgumentMatchers.eq("created"), anyString(), any());
    }

    @Test
    void recordFacilityAdminAffiliation_isIdempotent_whenActiveLinkAlreadyExists() throws Exception {
        AffiliationEntity existing = new AffiliationEntity();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenant);
        existing.setOrganizationId(orgId);
        existing.setSubjectType("FACILITY");
        existing.setSubjectRef(facilityUuid);
        existing.setAffiliationType("FACILITY_ADMINISTRATION");
        existing.setStatus("ACTIVE");

        // Early-return on an existing ACTIVE link never touches the organization lookup or save.
        when(affiliationRepository.findByOrganizationId(orgId)).thenReturn(List.of(existing));

        AffiliationEntity result = service.recordFacilityAdminAffiliation(tenant, orgId,
                new OrgRegistryDtos.RecordFacilityAdminAffiliationRequest(
                        facilityUuid, "HID-100", "national-admin-1", null));

        assertThat(result).isSameAs(existing);
        verify(affiliationRepository, never()).save(any());
    }
}
