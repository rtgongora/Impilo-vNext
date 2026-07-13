package zw.gov.mohcc.impilo.vito.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.vito.api.dto.ClientDemographicsUpdateRequest;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientIdentifierEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientIdentifierRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 3A — proves the demographics UPDATE path preserves all 9 extended
 * demographics (parity with the create path), upserts passportReference as an
 * identifier, and never silently nulls fields that were not supplied.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientUpdateServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClientIdentifierRepository identifierRepository;
    @Mock private VitoProperties vitoProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ClientUpdateService service;

    private UUID tenantId;
    private UUID healthId;

    @BeforeEach
    void setUp() {
        service = new ClientUpdateService(clientRepository, outboxRepository, identifierRepository,
                vitoProperties, objectMapper);
        tenantId = UUID.randomUUID();
        healthId = UUID.randomUUID();
        when(clientRepository.save(any(ClientEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(identifierRepository.save(any(ClientIdentifierEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ClientEntity existingClient() {
        ClientEntity c = new ClientEntity();
        c.setHealthId(healthId);
        c.setTenantId(tenantId);
        c.setGivenName("Tendai");
        c.setFamilyName("Moyo");
        when(clientRepository.findByTenantIdAndHealthId(tenantId, healthId)).thenReturn(Optional.of(c));
        return c;
    }

    private ClientDemographicsUpdateRequest allNineRequest() {
        return new ClientDemographicsUpdateRequest(
                null, "Rumbidzai", null, null, null, null,
                "12 Samora Ave", "Harare", null, null,
                "tendai@example.com", "ZW-PP-99887766", null, "sn", "MARRIED",
                "Chiedza Moyo", "+263770000000");
    }

    @Test
    void update_persistsAllNineExtendedDemographics() throws Exception {
        existingClient();
        when(identifierRepository.findByTenantIdAndClientHealthId(tenantId, healthId)).thenReturn(List.of());

        service.update(tenantId, healthId, allNineRequest(), "system-actor", UUID.randomUUID().toString());

        ArgumentCaptor<ClientEntity> saved = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository, times(1)).save(saved.capture());
        ClientEntity c = saved.getValue();

        assertEquals("Rumbidzai", c.getMiddleName(), "middleName");
        Map<String, Object> demographics = parse(c.getDemographics());
        assertEquals("sn", demographics.get("preferredLanguage"), "preferredLanguage");
        assertEquals("MARRIED", demographics.get("maritalStatus"), "maritalStatus");
        Map<String, Object> contacts = parse(c.getContacts());
        assertEquals("tendai@example.com", contacts.get("email"), "email");
        assertEquals("Chiedza Moyo", contacts.get("emergencyContactName"), "emergencyContactName");
        assertEquals("+263770000000", contacts.get("emergencyContactPhone"), "emergencyContactPhone");
        Map<String, Object> address = parse(c.getAddress());
        assertEquals("12 Samora Ave", address.get("addressLine1"), "addressLine1");
        assertEquals("Harare", address.get("city"), "city");

        // passportReference persisted as a PASSPORT_REFERENCE identifier row.
        ArgumentCaptor<ClientIdentifierEntity> ident = ArgumentCaptor.forClass(ClientIdentifierEntity.class);
        verify(identifierRepository, times(1)).save(ident.capture());
        assertEquals("PASSPORT_REFERENCE", ident.getValue().getIdentifierType());
        assertEquals("ZW-PP-99887766", ident.getValue().getIdentifierValue());
    }

    @Test
    void update_passportReference_upsertsExistingIdentifierInsteadOfDuplicating() {
        existingClient();
        ClientIdentifierEntity existing = new ClientIdentifierEntity();
        existing.setTenantId(tenantId);
        existing.setClientHealthId(healthId);
        existing.setIdentifierType("PASSPORT_REFERENCE");
        existing.setIdentifierValue("OLD-PP-0001");
        when(identifierRepository.findByTenantIdAndClientHealthId(tenantId, healthId))
                .thenReturn(List.of(existing));

        ClientDemographicsUpdateRequest req = new ClientDemographicsUpdateRequest(
                null, null, null, null, null, null, null, null, null, null,
                null, "NEW-PP-0002", null, null, null, null, null);
        service.update(tenantId, healthId, req, "system-actor", UUID.randomUUID().toString());

        ArgumentCaptor<ClientIdentifierEntity> ident = ArgumentCaptor.forClass(ClientIdentifierEntity.class);
        verify(identifierRepository, times(1)).save(ident.capture());
        assertEquals("NEW-PP-0002", ident.getValue().getIdentifierValue(), "existing identifier updated in place");
        assertEquals(existing, ident.getValue(), "no duplicate identifier row created");
    }

    @Test
    void update_medicalAidNumber_attachesIdentifierToHealthId() {
        existingClient();
        when(identifierRepository.findByTenantIdAndClientHealthId(tenantId, healthId)).thenReturn(List.of());

        ClientDemographicsUpdateRequest req = new ClientDemographicsUpdateRequest(
                null, null, null, null, null, null, null, null, null, null,
                null, null, "MA-77421", null, null, null, null);
        service.update(tenantId, healthId, req, "system-actor", UUID.randomUUID().toString());

        ArgumentCaptor<ClientIdentifierEntity> ident = ArgumentCaptor.forClass(ClientIdentifierEntity.class);
        verify(identifierRepository, times(1)).save(ident.capture());
        assertEquals("MEDICAL_AID_NUMBER", ident.getValue().getIdentifierType());
        assertEquals("MA-77421", ident.getValue().getIdentifierValue());
        assertEquals(healthId, ident.getValue().getClientHealthId(), "tied to the person's Health ID");
    }

    @Test
    void update_omittedExtendedFields_preserveExistingJson() throws Exception {
        ClientEntity c = existingClient();
        c.setContacts("{\"email\":\"old@example.com\"}");
        c.setDemographics("{\"preferredLanguage\":\"en\"}");

        ClientDemographicsUpdateRequest onlyGivenName = new ClientDemographicsUpdateRequest(
                "Updated", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        service.update(tenantId, healthId, onlyGivenName, "system-actor", UUID.randomUUID().toString());

        ArgumentCaptor<ClientEntity> saved = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository).save(saved.capture());
        ClientEntity result = saved.getValue();
        assertEquals("old@example.com", parse(result.getContacts()).get("email"), "email preserved");
        assertEquals("en", parse(result.getDemographics()).get("preferredLanguage"), "preferredLanguage preserved");
        verify(identifierRepository, never()).save(any());
    }

    @Test
    void update_blankExtendedFields_doNotClobberExistingValues() throws Exception {
        ClientEntity c = existingClient();
        c.setContacts("{\"email\":\"keep@example.com\"}");

        ClientDemographicsUpdateRequest blanks = new ClientDemographicsUpdateRequest(
                null, null, null, null, null, null, null, null, null, null,
                "   ", null, null, "", null, null, null);
        service.update(tenantId, healthId, blanks, "system-actor", UUID.randomUUID().toString());

        ArgumentCaptor<ClientEntity> saved = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository).save(saved.capture());
        assertEquals("keep@example.com", parse(saved.getValue().getContacts()).get("email"),
                "blank email must not clobber the stored value");
    }

    @Test
    void update_partialExtended_mergesWithoutReplacingSiblings() throws Exception {
        ClientEntity c = existingClient();
        c.setDemographics("{\"maritalStatus\":\"SINGLE\"}");
        when(identifierRepository.findByTenantIdAndClientHealthId(tenantId, healthId)).thenReturn(List.of());

        ClientDemographicsUpdateRequest req = new ClientDemographicsUpdateRequest(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, "nd", null, null, null);
        service.update(tenantId, healthId, req, "system-actor", UUID.randomUUID().toString());

        ArgumentCaptor<ClientEntity> saved = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository).save(saved.capture());
        Map<String, Object> demographics = parse(saved.getValue().getDemographics());
        assertEquals("nd", demographics.get("preferredLanguage"), "new field added");
        assertEquals("SINGLE", demographics.get("maritalStatus"), "sibling field preserved");
    }

    @Test
    void update_stillPublishesDemographicsUpdatedEvent() {
        existingClient();
        when(identifierRepository.findByTenantIdAndClientHealthId(tenantId, healthId)).thenReturn(List.of());

        service.update(tenantId, healthId, allNineRequest(), "system-actor", UUID.randomUUID().toString());

        ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, times(1)).save(ev.capture());
        assertEquals("CLIENT_DEMOGRAPHICS_UPDATED", ev.getValue().getEventType());
    }

    @Test
    void update_unknownHealthId_returns404() {
        when(clientRepository.findByTenantIdAndHealthId(eq(tenantId), any())).thenReturn(Optional.empty());
        UUID missing = UUID.randomUUID();
        try {
            service.update(tenantId, missing, allNineRequest(), "system-actor", UUID.randomUUID().toString());
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            assertEquals(404, ex.getStatusCode().value());
            return;
        }
        assertTrue(false, "expected 404 ResponseStatusException");
    }

    private Map<String, Object> parse(String json) throws Exception {
        assertNotNull(json, "json blob should not be null");
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}
