package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PatientDocumentEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PatientDocumentRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatientDocumentServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SUBJECT = "CPID-ADULT-1";

    @Mock private PatientDocumentRepository documentRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClinicalAccessGuard accessGuard;

    private PatientDocumentService service;
    private TrustContext context;

    @BeforeEach
    void setUp() {
        service = new PatientDocumentService(documentRepository, outboxRepository,
                new ObjectMapper(), accessGuard);
        context = new TrustContext(TENANT, "clerk-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
        when(documentRepository.save(any(PatientDocumentEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private PatientDocumentEntity create(Consumer<Map<String, Object>> customiser) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", SUBJECT);
        body.put("encounter_id", "42");
        body.put("document_type", "lab_report");
        body.put("title", "FBC result");
        body.put("storage_key", "objects/2026/07/fbc.pdf");
        customiser.accept(body);
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            return service.create(body);
        }
    }

    @Test
    void aDocumentIndexEntryRequiresTypeTitleAndStorageKey() {
        assertThatThrownBy(() -> create(body -> body.remove("storage_key")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("storage_key");
        verify(documentRepository, never()).save(any());
    }

    @Test
    void theSubjectRelationshipIsBoundBeforeAnythingIsWritten() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no care context"))
                .when(accessGuard).requireCareRelationship(any(), any(), any(), any());
        assertThatThrownBy(() -> create(body -> { }))
                .isInstanceOf(ResponseStatusException.class);
        verify(documentRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void aCreateNormalisesTypeAndLeavesACpidOnlyEventOnTheOutbox() {
        PatientDocumentEntity saved = create(body -> {
            body.put("file_size", 12345);
            body.put("document_object_id", "6f9619ff-8b86-d011-b42d-00c04fc964ff");
        });

        assertThat(saved.getDocumentType()).isEqualTo("LAB_REPORT");
        assertThat(saved.getSubjectCpid()).isEqualTo(SUBJECT);
        assertThat(saved.getFileSizeBytes()).isEqualTo(12345L);
        assertThat(saved.getDocumentObjectId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<EventOutboxEntity> event = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("pct.patient-document.recorded");
        // The index fact only — never the title or description, which are free text a person typed.
        assertThat(event.getValue().getPayload()).doesNotContain("FBC result");
        assertThat(event.getValue().getPayload()).contains(SUBJECT).contains("LAB_REPORT");
    }

    @Test
    void theCitizenReadBindsTheCallerToTheirOwnRecord_withoutDisclosingExistence() {
        PatientDocumentEntity row = new PatientDocumentEntity();
        row.setDocumentId(UUID.randomUUID());
        row.setTenantId(TENANT);
        row.setSubjectCpid(SUBJECT);
        when(documentRepository.findByTenantIdAndDocumentId(TENANT, row.getDocumentId()))
                .thenReturn(Optional.of(row));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            assertThat(service.get(row.getDocumentId().toString(), SUBJECT)).isSameAs(row);
            assertThatThrownBy(() -> service.get(row.getDocumentId().toString(), "CPID-SOMEBODY-ELSE"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
