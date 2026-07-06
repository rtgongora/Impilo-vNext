package zw.gov.mohcc.impilo.governance.mirror;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import zw.gov.mohcc.impilo.governance.core.OrganisationService;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end proof that a mirror failure can NEVER fail or roll back the primary
 * {@code wgv_organisation} write. Runs the real Spring context (H2, real
 * OrganisationService + AFTER_COMMIT relay) with the mirror producer ENABLED and
 * a mirror client that always throws.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wgv_mirror_tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "impilo.org-mirror.enabled=true"
})
@ActiveProfiles("test")
class OrgMirrorTransactionIntegrityTest {

    @Autowired
    private OrganisationService organisationService;
    @Autowired
    private OrganisationRepository organisationRepository;

    @MockBean
    private OrgRegistryMirrorClient mirrorClient;

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void mirrorClientThrows_primaryOrganisationStillPersisted() {
        when(mirrorClient.push(any(), any(), any())).thenThrow(new RuntimeException("mirror receiver down"));

        UUID tenant = UUID.randomUUID();
        TrustContextHolder.set(new TrustContext(
                tenant, "actor-1", "OPERATOR", "OPERATIONS", "fp",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        OrganisationEntity org = assertDoesNotThrow(() -> organisationService.createOrganisation(
                tenant, "ORG-TX-1", "Tx Org", "Tx Org Ltd", "HOSPITAL_GROUP", null, null));

        // Primary write committed and is readable despite the mirror push throwing.
        assertTrue(organisationRepository.findById(org.getId()).isPresent(),
                "organisation must be durably persisted even when the mirror push fails");
        // The relay did attempt the (failing) mirror push AFTER_COMMIT.
        verify(mirrorClient, atLeastOnce()).push(any(), any(), any());
    }
}
