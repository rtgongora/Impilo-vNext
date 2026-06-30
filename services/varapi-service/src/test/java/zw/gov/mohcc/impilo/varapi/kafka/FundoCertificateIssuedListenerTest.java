package zw.gov.mohcc.impilo.varapi.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.core.FundoCpdGovernanceService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FundoCertificateIssuedListenerTest {

    @Mock private FundoCpdGovernanceService cpdGovernanceService;

    private FundoCertificateIssuedListener listener() {
        return new FundoCertificateIssuedListener(cpdGovernanceService, new ObjectMapper());
    }

    @Test
    void lands_a_cpd_eligible_provider_certificate_as_a_candidate() {
        UUID tenant = UUID.randomUUID();
        String payload = String.format(
                "{\"tenantId\":\"%s\",\"subjectType\":\"PROVIDER\",\"subjectId\":\"PRV-1\","
                        + "\"courseId\":\"C-1\",\"title\":\"Infection Control\",\"issuedAt\":\"2026-06-30T08:00:00Z\","
                        + "\"certificateNumber\":\"CERT-9\",\"cpdEligible\":true,\"suggestedCpdPoints\":5}", tenant);

        listener().onCertificateIssued(payload);

        verify(cpdGovernanceService).ingestCompletion(eq(tenant), eq("PRV-1"), eq(null), eq("C-1"),
                eq("Infection Control"), eq(Instant.parse("2026-06-30T08:00:00Z")), eq(5), eq("CERT-9"), eq(payload));
    }

    @Test
    void ignores_a_certificate_that_is_not_cpd_eligible() {
        String payload = "{\"tenantId\":\"" + UUID.randomUUID()
                + "\",\"subjectType\":\"PROVIDER\",\"subjectId\":\"PRV-1\",\"cpdEligible\":false}";
        listener().onCertificateIssued(payload);
        verify(cpdGovernanceService, never()).ingestCompletion(any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void ignores_a_non_provider_subject() {
        // A citizen learner's certificate is not a CPD candidate.
        String payload = "{\"tenantId\":\"" + UUID.randomUUID()
                + "\",\"subjectType\":\"CITIZEN\",\"subjectId\":\"HID-1\",\"cpdEligible\":true}";
        listener().onCertificateIssued(payload);
        verify(cpdGovernanceService, never()).ingestCompletion(any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void malformed_json_is_swallowed() {
        listener().onCertificateIssued("not-json {{");
        verify(cpdGovernanceService, never()).ingestCompletion(any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }
}
