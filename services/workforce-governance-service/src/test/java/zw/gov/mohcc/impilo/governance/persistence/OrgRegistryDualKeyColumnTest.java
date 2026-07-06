package zw.gov.mohcc.impilo.governance.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the phase-2a dual-key columns (V009 {@code org_registry_org_id}) exist
 * on the FK-carrier tables and round-trip through the entity mapping as a
 * nullable UUID — null by default, and persisted when set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrgRegistryDualKeyColumnTest {

    @Autowired
    TestEntityManager em;

    @Test
    void facilityOrganisationLink_dualKey_nullByDefault_andRoundTrips() {
        UUID tenant = UUID.randomUUID();
        FacilityOrganisationLinkEntity link = new FacilityOrganisationLinkEntity(
                UUID.randomUUID(), tenant, 10L, UUID.randomUUID(), "OWNER", "ACTIVE");

        UUID id = em.persistAndGetId(link, UUID.class);
        em.flush();
        em.clear();

        FacilityOrganisationLinkEntity reloaded = em.find(FacilityOrganisationLinkEntity.class, id);
        assertThat(reloaded.getOrgRegistryOrgId()).isNull();

        UUID orgRegistryId = UUID.randomUUID();
        reloaded.setOrgRegistryOrgId(orgRegistryId);
        em.persist(reloaded);
        em.flush();
        em.clear();

        assertThat(em.find(FacilityOrganisationLinkEntity.class, id).getOrgRegistryOrgId()).isEqualTo(orgRegistryId);
    }

    @Test
    void hscEmployment_dualKey_roundTrips() {
        HscEmploymentEntity hsc = new HscEmploymentEntity();
        hsc.init(UUID.randomUUID());
        hsc.setProviderWorkerId("PW-1");
        hsc.setEmploymentStatus("ACTIVE");
        UUID orgRegistryId = UUID.randomUUID();
        hsc.setOrgRegistryOrgId(orgRegistryId);

        UUID id = em.persistAndGetId(hsc, UUID.class);
        em.flush();
        em.clear();

        assertThat(em.find(HscEmploymentEntity.class, id).getOrgRegistryOrgId()).isEqualTo(orgRegistryId);
    }

    @Test
    void organisationUnit_membership_site_dualKeyColumnsExist() {
        UUID tenant = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        OrganisationUnitEntity unit = new OrganisationUnitEntity(
                UUID.randomUUID(), tenant, orgId, "U-1", "Unit One", "DEPARTMENT", "ACTIVE");
        unit.setOrgRegistryOrgId(UUID.randomUUID());
        UUID unitId = em.persistAndGetId(unit, UUID.class);

        OrganisationMembershipEntity membership = new OrganisationMembershipEntity();
        membership.init(tenant, orgId, "subject-1", "USER", "ADMIN", "ACTIVE");
        membership.setOrgRegistryOrgId(UUID.randomUUID());
        UUID membershipId = em.persistAndGetId(membership, UUID.class);

        SiteOrganisationLinkEntity site = new SiteOrganisationLinkEntity(
                UUID.randomUUID(), tenant, UUID.randomUUID(), orgId, "HOSTED_AT", "ACTIVE");
        site.setOrgRegistryOrgId(UUID.randomUUID());
        UUID siteId = em.persistAndGetId(site, UUID.class);

        em.flush();
        em.clear();

        assertThat(em.find(OrganisationUnitEntity.class, unitId).getOrgRegistryOrgId()).isNotNull();
        assertThat(em.find(OrganisationMembershipEntity.class, membershipId).getOrgRegistryOrgId()).isNotNull();
        assertThat(em.find(SiteOrganisationLinkEntity.class, siteId).getOrgRegistryOrgId()).isNotNull();
    }
}
