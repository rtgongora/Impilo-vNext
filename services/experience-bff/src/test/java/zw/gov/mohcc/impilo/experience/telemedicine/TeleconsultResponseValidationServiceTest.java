package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.ZiboServiceClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TeleconsultResponseValidationServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ZiboServiceClient zibo = Mockito.mock(ZiboServiceClient.class);
    private final VarapiServiceClient varapi = Mockito.mock(VarapiServiceClient.class);
    private final PctServiceClient pct = Mockito.mock(PctServiceClient.class);

    private TeleconsultResponseValidationService service(String mode) {
        return new TeleconsultResponseValidationService(zibo, varapi, pct, mapper, mode);
    }

    @Test
    void flagsInvalidCodedDiagnosis_againstZibo() {
        Mockito.when(zibo.validateCoding(eq("ICD-10"), eq("XXX"), any()))
                .thenReturn(mapper.createObjectNode().put("valid", false).put("systemRegistered", true));
        var outcome = service("SHADOW").validate(
                Map.of("codedDiagnosis", Map.of("system", "ICD-10", "code", "XXX")), "actor-1", "CPID-1");
        assertThat(outcome.issues()).anyMatch(i -> i.code().equals("CODED_DIAGNOSIS_INVALID") && i.severity().equals("ERROR"));
    }

    @Test
    void errorsWhenPrescriberNotActive() {
        Mockito.when(varapi.getProvider(eq("prov-x")))
                .thenReturn(mapper.createObjectNode().put("status", "SUSPENDED"));
        var outcome = service("SHADOW").validate(
                Map.of("prescriptions", List.of(Map.of("medication", "Amoxicillin")), "responderId", "prov-x"),
                "actor-1", "CPID-1");
        assertThat(outcome.hasErrors()).isTrue();
        assertThat(outcome.issues()).anyMatch(i -> i.code().equals("PRESCRIBER_NOT_AUTHORIZED"));
    }

    @Test
    void passesWhenPrescriberActiveAndNoOtherIssues() {
        Mockito.when(varapi.getProvider(any()))
                .thenReturn(mapper.createObjectNode().put("status", "ACTIVE"));
        Mockito.when(pct.listAllergies(any())).thenReturn(mapper.createArrayNode());
        var outcome = service("SHADOW").validate(
                Map.of("prescriptions", List.of(Map.of("medication", "Amoxicillin")), "responderId", "prov-ok"),
                "actor-1", "CPID-1");
        assertThat(outcome.hasErrors()).isFalse();
    }

    @Test
    void flagsAllergyConflict_whenPrescribedMedMatchesRecordedAllergy() {
        Mockito.when(varapi.getProvider(any())).thenReturn(mapper.createObjectNode().put("status", "ACTIVE"));
        var allergyList = mapper.createArrayNode();
        allergyList.addObject().put("substance", "Penicillin");
        Mockito.when(pct.listAllergies(eq("CPID-1"))).thenReturn(allergyList);
        var outcome = service("SHADOW").validate(
                Map.of("prescriptions", List.of(Map.of("medication", "Penicillin V")), "responderId", "prov-ok"),
                "actor-1", "CPID-1");
        assertThat(outcome.issues()).anyMatch(i -> i.code().equals("ALLERGY_CONFLICT") && i.severity().equals("ERROR"));
    }

    @Test
    void degradesToWarning_whenAllergyServiceUnavailable_notFalseAllClear() {
        Mockito.when(varapi.getProvider(any())).thenReturn(mapper.createObjectNode().put("status", "ACTIVE"));
        Mockito.when(pct.listAllergies(any())).thenThrow(new RuntimeException("404 allergies"));
        var outcome = service("SHADOW").validate(
                Map.of("prescriptions", List.of(Map.of("medication", "Amoxicillin")), "responderId", "prov-ok"),
                "actor-1", "CPID-1");
        assertThat(outcome.issues()).anyMatch(i -> i.code().equals("ALLERGIES_UNVERIFIED") && i.severity().equals("WARNING"));
        assertThat(outcome.hasErrors()).isFalse();
    }

    @Test
    void enforceBlocksOnError_shadowDoesNot() {
        Mockito.when(varapi.getProvider(any())).thenReturn(mapper.createObjectNode().put("status", "REVOKED"));
        Map<String, Object> body = Map.of("prescriptions", List.of(Map.of("medication", "X")), "responderId", "p");

        assertThat(service("SHADOW").validate(body, "a", "CPID-1").blocks()).isFalse();
        assertThat(service("ENFORCE").validate(body, "a", "CPID-1").blocks()).isTrue();
    }

    @Test
    void offMode_runsNoChecks() {
        var outcome = service("OFF").validate(
                Map.of("prescriptions", List.of(Map.of("medication", "X")), "responderId", "p"), "a", "CPID-1");
        assertThat(outcome.issues()).isEmpty();
        Mockito.verifyNoInteractions(varapi, zibo, pct);
    }
}
