package zw.gov.mohcc.impilo.learning.fundo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundoLiveCompletionServiceTest {

    @Mock
    private FundoEnrolmentService enrolmentService;
    @Mock
    private FundoProgressService progressService;
    @Mock
    private FundoCertificateService certificateService;

    private FundoLiveCompletionService service;
    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new FundoLiveCompletionService(enrolmentService, progressService, certificateService);
    }

    @Test
    void recordLiveCompletionEnrolsProgressAndIssuesCertificate() {
        UUID courseId = UUID.randomUUID();
        UUID enrolmentId = UUID.randomUUID();
        Map<String, Object> enrolment = Map.of("id", enrolmentId.toString());

        when(enrolmentService.create(any(FundoEnrolmentService.EnrolmentRequest.class))).thenReturn(enrolment);
        when(certificateService.issueForEnrolment(eq(tenantId), eq(enrolmentId)))
                .thenReturn(new FundoCertificateService.CertificateIssueResult(
                        Map.of("id", "cert-1"), false));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("courseId", courseId.toString());
        body.put("subjectId", "provider-42");
        body.put("liveEventId", UUID.randomUUID().toString());
        body.put("cpdPoints", 2);

        Map<String, Object> result = service.recordLiveCompletion(tenantId, body);

        assertThat(result.get("enrolmentId")).isEqualTo(enrolmentId.toString());
        assertThat(result.get("certificateIssued")).isEqualTo(true);
        assertThat(result.get("courseId")).isEqualTo(courseId.toString());
    }

    @Test
    void recordLiveCompletionRequiresCourseAndSubject() {
        assertThatThrownBy(() -> service.recordLiveCompletion(tenantId, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("courseId and subjectId are required");
    }
}
