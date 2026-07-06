package zw.gov.mohcc.impilo.governance.mirror;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import zw.gov.mohcc.impilo.governance.core.GovernanceEventService;
import zw.gov.mohcc.impilo.governance.core.OrganisationService;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the dual-write hook: a successful {@code wgv_organisation} write
 * publishes an {@link OrgMirrorEvent} carrying a faithful payload.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganisationServiceMirrorTest {

    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private GovernanceEventService governanceEventService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrganisationService service;

    private final UUID tenant = UUID.randomUUID();
    private final UUID correlation = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrganisationService(organisationRepository, governanceEventService, eventPublisher);
        TrustContextHolder.set(new TrustContext(
                tenant, "actor-1", "OPERATOR", "OPERATIONS", "fp",
                correlation, null, null, null, AccessMode.INTERNAL));
        when(organisationRepository.findByTenantIdAndOrganisationCode(any(), any())).thenReturn(Optional.empty());
        when(organisationRepository.save(any(OrganisationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void createOrganisation_publishesMirrorEventWithPayload() {
        OrganisationEntity org = service.createOrganisation(
                tenant, "ORG-77", "Test Org", "Test Org Ltd", "HOSPITAL_GROUP", null, null);

        ArgumentCaptor<OrgMirrorEvent> captor = ArgumentCaptor.forClass(OrgMirrorEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        OrgMirrorEvent event = captor.getValue();
        assertNotNull(event);
        assertEquals("created", event.changeType());
        assertEquals(tenant, event.tenantId());
        assertEquals(correlation, event.correlationId());
        assertEquals(org.getId(), event.payload().id());
        assertEquals("ORG-77", event.payload().code());
        assertEquals("Test Org Ltd", event.payload().legalName());
        assertEquals("HOSPITAL_GROUP", event.payload().organisationType());
        assertEquals(org.getStatus(), event.payload().status());
    }

    @Test
    void updateStatus_publishesUpdatedMirrorEvent() {
        UUID orgId = UUID.randomUUID();
        OrganisationEntity existing = new OrganisationEntity(
                orgId, tenant, "ORG-9", "Org Nine", "HOSPITAL_GROUP", "DRAFT");
        when(organisationRepository.findById(orgId)).thenReturn(Optional.of(existing));

        service.updateStatus(orgId,
                zw.gov.mohcc.impilo.governance.domain.GovernanceEnums.OrganisationStatus.ACTIVE);

        ArgumentCaptor<OrgMirrorEvent> captor = ArgumentCaptor.forClass(OrgMirrorEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertEquals("updated", captor.getValue().changeType());
        assertEquals("ACTIVE", captor.getValue().payload().status());
    }
}
