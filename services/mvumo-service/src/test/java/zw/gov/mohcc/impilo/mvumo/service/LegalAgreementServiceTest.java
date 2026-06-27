package zw.gov.mohcc.impilo.mvumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.LegalAgreementEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.LegalAgreementRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegalAgreementServiceTest {

    @Mock private LegalAgreementRepository repository;
    @Mock private ConsentEventRepository consentEventRepository;
    @Mock private EventOutboxRepository eventOutboxRepository;

    private LegalAgreementService service;
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String SUBJECT = "cpid-123";

    @BeforeEach
    void setUp() {
        service = new LegalAgreementService(repository, consentEventRepository, eventOutboxRepository, new ObjectMapper());
        // DB assigns the id on save; emulate so toView/audit have a non-null id.
        when(repository.save(any())).thenAnswer(inv -> {
            LegalAgreementEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void accept_persistsGranted_andEmitsEventAndOutbox() {
        when(repository.findByTenantIdAndSubjectActorIdAndDocumentTypeAndState(any(), any(), any(), any()))
                .thenReturn(List.of());

        Map<String, Object> view = service.accept(TENANT, Map.of(
                "subjectActorId", SUBJECT, "documentType", "terms_of_use", "version", "2026-04-11",
                "channel", "web"));

        assertEquals("TERMS_OF_USE", view.get("documentType"));
        assertEquals("GRANTED", view.get("state"));
        assertEquals("WEB", view.get("channel"));
        verify(consentEventRepository).save(any());
        verify(eventOutboxRepository).save(any());
    }

    @Test
    void accept_newVersion_supersedesPriorGranted() {
        LegalAgreementEntity prior = granted("TERMS_OF_USE", "v1");
        when(repository.findByTenantIdAndSubjectActorIdAndDocumentTypeAndState(
                eq(TENANT), eq(SUBJECT), eq("TERMS_OF_USE"), eq("GRANTED")))
                .thenReturn(List.of(prior));

        Map<String, Object> view = service.accept(TENANT, Map.of(
                "subjectActorId", SUBJECT, "documentType", "TERMS_OF_USE", "version", "v2"));

        assertEquals("v2", view.get("version"));
        assertEquals("GRANTED", view.get("state"));
        assertEquals("SUPERSEDED", prior.getState(), "prior version must be superseded");
    }

    @Test
    void accept_sameVersion_isIdempotent_noNewEvent() {
        LegalAgreementEntity prior = granted("TERMS_OF_USE", "v1");
        when(repository.findByTenantIdAndSubjectActorIdAndDocumentTypeAndState(
                eq(TENANT), eq(SUBJECT), eq("TERMS_OF_USE"), eq("GRANTED")))
                .thenReturn(List.of(prior));

        service.accept(TENANT, Map.of("subjectActorId", SUBJECT, "documentType", "TERMS_OF_USE", "version", "v1"));

        verify(eventOutboxRepository, never()).save(any());
        verify(consentEventRepository, never()).save(any());
    }

    @Test
    void accept_unknownDocumentType_rejected() {
        assertThrows(ResponseStatusException.class, () -> service.accept(TENANT, Map.of(
                "subjectActorId", SUBJECT, "documentType", "MARKETING", "version", "v1")));
    }

    @Test
    void withdraw_setsWithdrawnState_andEmits() {
        LegalAgreementEntity e = granted("PRIVACY_POLICY", "v1");
        e.setId(UUID.randomUUID());
        when(repository.findByIdAndTenantId(eq(e.getId()), eq(TENANT))).thenReturn(java.util.Optional.of(e));

        Map<String, Object> view = service.withdraw(TENANT, e.getId(), "self");

        assertEquals("WITHDRAWN", view.get("state"));
        verify(eventOutboxRepository).save(any());
    }

    @Test
    void evaluate_reportsMissingAndStale() {
        // PRIVACY_POLICY granted at old version (stale vs required v2); TERMS_OF_USE not granted (missing).
        when(repository.findByTenantIdAndSubjectActorIdAndDocumentTypeAndState(
                eq(TENANT), eq(SUBJECT), eq("PRIVACY_POLICY"), eq("GRANTED")))
                .thenReturn(List.of(granted("PRIVACY_POLICY", "v1")));
        when(repository.findByTenantIdAndSubjectActorIdAndDocumentTypeAndState(
                eq(TENANT), eq(SUBJECT), eq("TERMS_OF_USE"), eq("GRANTED")))
                .thenReturn(List.of());

        Map<String, Object> result = service.evaluate(TENANT, SUBJECT,
                Map.of("PRIVACY_POLICY", "v2", "TERMS_OF_USE", "v2"));

        assertFalse((Boolean) result.get("currentlyAccepted"));
        assertTrue(((List<?>) result.get("missing")).contains("TERMS_OF_USE"));
        assertTrue(((List<?>) result.get("staleVersions")).contains("PRIVACY_POLICY"));
    }

    @Test
    void evaluate_allAcceptedCurrent_isTrue() {
        when(repository.findByTenantIdAndSubjectActorIdAndDocumentTypeAndState(
                eq(TENANT), eq(SUBJECT), eq("PRIVACY_POLICY"), eq("GRANTED")))
                .thenReturn(List.of(granted("PRIVACY_POLICY", "v2")));

        Map<String, Object> result = service.evaluate(TENANT, SUBJECT, Map.of("PRIVACY_POLICY", "v2"));

        assertTrue((Boolean) result.get("currentlyAccepted"));
        assertTrue(((List<?>) result.get("missing")).isEmpty());
        assertTrue(((List<?>) result.get("staleVersions")).isEmpty());
    }

    private LegalAgreementEntity granted(String documentType, String version) {
        LegalAgreementEntity e = new LegalAgreementEntity();
        e.setId(UUID.randomUUID()); // persisted rows always carry an id
        e.setTenantId(TENANT);
        e.setSubjectActorId(SUBJECT);
        e.setDocumentType(documentType);
        e.setVersion(version);
        e.setState(LegalAgreementEntity.State.GRANTED.name());
        e.setChannel("WEB");
        return e;
    }
}
