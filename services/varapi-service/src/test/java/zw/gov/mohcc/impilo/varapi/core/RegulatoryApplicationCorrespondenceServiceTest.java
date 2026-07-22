package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.core.RegulatoryApplicationCorrespondenceService.RecusalRequiredException;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationCaseMessageEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderApplicationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ApplicationCaseMessageRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ApplicationInformationRequestRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderApplicationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegulatoryApplicationCorrespondenceServiceTest {

    @Mock ProviderApplicationRepository applicationRepository;
    @Mock ApplicationInformationRequestRepository rfiRepository;
    @Mock ApplicationCaseMessageRepository messageRepository;

    RegulatoryApplicationCorrespondenceService service;
    UUID tenant = UUID.randomUUID();
    UUID subjectHealthId = UUID.randomUUID();

    private ProviderApplicationEntity application() {
        ProviderEntity subject = new ProviderEntity();
        subject.setImpiloHealthId(subjectHealthId);
        ProviderApplicationEntity app = new ProviderApplicationEntity();
        app.setProvider(subject);
        return app;
    }

    @BeforeEach
    void setUp() {
        service = new RegulatoryApplicationCorrespondenceService(applicationRepository, rfiRepository, messageRepository);
        lenient().when(applicationRepository.findByIdAndTenantId(any(), any()))
                .thenReturn(Optional.of(application()));
        lenient().when(rfiRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(messageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void regulatorCannotOpenRfiOnOwnApplication() {
        // The acting person IS the application's subject → recusal.
        assertThatThrownBy(() -> service.openRfi(tenant, subjectHealthId.toString(), 1L, "Please clarify", null))
                .isInstanceOf(RecusalRequiredException.class);
    }

    @Test
    void adifferentRegulatorMayOpenRfi() {
        var rfi = service.openRfi(tenant, UUID.randomUUID().toString(), 1L, "Please clarify", null);
        assertThat(rfi.getStatus()).isEqualTo("OPEN");
        assertThat(rfi.getMessage()).isEqualTo("Please clarify");
    }

    @Test
    void internalMessageNeverAppearsInApplicantThread() {
        // The applicant thread queries visibility=APPLICANT only.
        when(messageRepository.findByTenantIdAndApplicationIdAndVisibilityOrderByCreatedAtAsc(tenant, 1L, "APPLICANT"))
                .thenReturn(List.of(applicantMsg()));
        List<ApplicationCaseMessageEntity> thread = service.applicantThread(tenant, 1L);
        assertThat(thread).allSatisfy(m -> assertThat(m.getVisibility()).isEqualTo("APPLICANT"));
    }

    @Test
    void regulatorInternalNoteIsRecusalGuarded() {
        assertThatThrownBy(() -> service.postMessage(tenant, subjectHealthId.toString(), 1L,
                "OUTBOUND", "INTERNAL", "REGISTRAR", "internal note")).isInstanceOf(RecusalRequiredException.class);
    }

    private static ApplicationCaseMessageEntity applicantMsg() {
        ApplicationCaseMessageEntity m = new ApplicationCaseMessageEntity();
        m.setVisibility("APPLICANT");
        m.setBody("hello");
        return m;
    }
}
