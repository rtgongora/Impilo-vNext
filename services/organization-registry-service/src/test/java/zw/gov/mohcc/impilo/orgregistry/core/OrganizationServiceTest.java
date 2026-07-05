package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AuthorizedRepresentativeEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgType;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.VerificationStatus;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AuthorizedRepresentativeRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    OrganizationRepository organizationRepository;
    @Mock
    AuthorizedRepresentativeRepository representativeRepository;
    @Mock
    OrgRegistryOutboxWriter outboxWriter;

    OrganizationService service;

    UUID tenant;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(
                organizationRepository, representativeRepository, outboxWriter, new ObjectMapper());
        tenant = UUID.randomUUID();
    }

    private static OrganizationEntity savedAnswer(org.mockito.invocation.InvocationOnMock inv) {
        OrganizationEntity o = inv.getArgument(0);
        if (o.getId() == null) {
            o.setId(UUID.randomUUID());
        }
        return o;
    }

    @Test
    void onboardingJourney_happyPath_createDraftSubmitAndVerify() throws Exception {
        // 1) create DRAFT
        when(organizationRepository.findByCode("ORG-001")).thenReturn(Optional.empty());
        when(organizationRepository.save(any(OrganizationEntity.class)))
                .thenAnswer(OrganizationServiceTest::savedAnswer);

        OrganizationEntity draft = service.createDraft(tenant,
                new OrgRegistryDtos.CreateOrganizationRequest(
                        "ORG-001", "Ministry Partner Org", "NGO_IMPLEMENTING_PARTNER", null, null),
                "admin-1");

        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(draft.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(draft.getSource()).isEqualTo("NATIVE");
        assertThat(draft.getOrgType()).isEqualTo(OrgType.NGO_IMPLEMENTING_PARTNER);

        // 2) add representative
        when(organizationRepository.findByTenantIdAndId(tenant, draft.getId()))
                .thenReturn(Optional.of(draft));
        when(representativeRepository.save(any(AuthorizedRepresentativeEntity.class))).thenAnswer(inv -> {
            AuthorizedRepresentativeEntity r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        AuthorizedRepresentativeEntity rep = service.addRepresentative(tenant, draft.getId(),
                new OrgRegistryDtos.AddRepresentativeRequest(
                        "HID-100", "Country Director", "doc://evidence/1", null, null, null),
                "admin-1");

        assertThat(rep.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_VERIFICATION);

        // 3) submit for verification
        when(representativeRepository.findByOrganizationId(draft.getId())).thenReturn(List.of(rep));

        OrganizationEntity submitted = service.submitVerification(tenant, draft.getId(), "rep-actor");
        assertThat(submitted.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_VERIFICATION);

        // 4) national-admin verify, attesting the representative
        when(representativeRepository.findByOrganizationIdAndId(draft.getId(), rep.getId()))
                .thenReturn(Optional.of(rep));
        when(representativeRepository.existsByOrganizationIdAndVerificationStatus(
                draft.getId(), VerificationStatus.VERIFIED)).thenReturn(true);

        OrganizationEntity verified = service.verify(tenant, draft.getId(), "national-admin-1",
                List.of(rep.getId()));

        assertThat(verified.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(verified.getStatus()).isEqualTo("ACTIVE");
        assertThat(verified.getVerifiedBy()).isEqualTo("national-admin-1");
        assertThat(verified.getVerifiedAt()).isNotNull();
        assertThat(rep.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        verify(outboxWriter, org.mockito.Mockito.atLeast(4))
                .publish(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void verify_requiresVerifiedRepresentative() {
        OrganizationEntity org = new OrganizationEntity();
        org.setId(UUID.randomUUID());
        org.setTenantId(tenant);
        org.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);

        when(organizationRepository.findByTenantIdAndId(tenant, org.getId()))
                .thenReturn(Optional.of(org));
        when(representativeRepository.existsByOrganizationIdAndVerificationStatus(
                org.getId(), VerificationStatus.VERIFIED)).thenReturn(false);

        assertThatThrownBy(() -> service.verify(tenant, org.getId(), "national-admin-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VERIFIED authorized representative");
        assertThat(org.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_VERIFICATION);
    }

    @Test
    void verify_rejectsOrganizationNotPendingVerification() {
        OrganizationEntity org = new OrganizationEntity();
        org.setId(UUID.randomUUID());
        org.setTenantId(tenant);
        org.setVerificationStatus(VerificationStatus.UNVERIFIED);

        when(organizationRepository.findByTenantIdAndId(tenant, org.getId()))
                .thenReturn(Optional.of(org));

        assertThatThrownBy(() -> service.verify(tenant, org.getId(), "national-admin-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending verification");
    }

    @Test
    void submitVerification_requiresAtLeastOneRepresentative() {
        OrganizationEntity org = new OrganizationEntity();
        org.setId(UUID.randomUUID());
        org.setTenantId(tenant);
        org.setVerificationStatus(VerificationStatus.UNVERIFIED);

        when(organizationRepository.findByTenantIdAndId(tenant, org.getId()))
                .thenReturn(Optional.of(org));
        when(representativeRepository.findByOrganizationId(org.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitVerification(tenant, org.getId(), "actor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authorized representative");
    }

    @Test
    void createDraft_rejectsDuplicateCode() {
        when(organizationRepository.findByCode("ORG-001"))
                .thenReturn(Optional.of(new OrganizationEntity()));

        assertThatThrownBy(() -> service.createDraft(tenant,
                new OrgRegistryDtos.CreateOrganizationRequest(
                        "ORG-001", "Duplicate Org", "OTHER", null, null),
                "admin-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }
}
