package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProgrammeEnrolmentEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProgrammeEnrolmentRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A patient-level register is refused for the programmes on the confidential lane.
 *
 * <p>A register listing names, for every person on it, that they are on it. For hypertension that is
 * a recall worklist. For HIV care it is a list of people's HIV status, and it is the most sensitive
 * artefact this system could produce. {@code SPECIALLY_PROTECTED} exists for exactly this and ships
 * in shadow — {@code CATEGORY_MAP_RATIFIED} is false — so serving the list would attach a protection
 * label that restricts nothing while telling everyone downstream the problem was handled.</p>
 *
 * <p>The refusal is deliberately not an empty list: an empty HIV register reads as "nobody is in HIV
 * care here", which is both false and reassuring. It names the missing control instead.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConfidentialRegisterRefusalTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000021");

    @Autowired
    private ProgrammeEnrolmentService service;

    @Autowired
    private ProgrammeEnrolmentRepository repository;

    @BeforeEach
    void setTrustContext() {
        TrustContextHolder.set(new TrustContext(TENANT, "actor-1", "PRACTITIONER", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clearTrustContext() {
        TrustContextHolder.clear();
    }

    private void save(String programme, String facility) {
        ProgrammeEnrolmentEntity e = new ProgrammeEnrolmentEntity();
        e.setEnrolmentId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setSubjectCpid("cpid-" + UUID.randomUUID());
        e.setProgramme(programme);
        e.setStatus("ON_TREATMENT");
        e.setAnchorProblemId(UUID.randomUUID());
        e.setEnrolledOn(LocalDate.of(2026, 1, 1));
        e.setManagingFacilityId(facility);
        e.setCreatedBy("test");
        repository.save(e);
    }

    @Test
    void anHivCareRegisterIsRefusedRatherThanServed() {
        save(ProgrammeVocabulary.HIV_CARE, "fac-1");

        assertThatThrownBy(() -> service.register(ProgrammeVocabulary.HIV_CARE, "fac-1", null))
                .isInstanceOf(ConfidentialRegisterException.class)
                .hasMessageContaining("CATEGORY_MAP_RATIFIED")
                .hasMessageContaining("cohort-counts");
    }

    @Test
    void prepIsRefusedToo() {
        // PrEP is HIV content about an HIV-negative person's risk, and a PrEP register is a list of
        // people someone judged to be at ongoing risk of HIV. It is on the same lane as HIV care.
        assertThatThrownBy(() -> service.register(ProgrammeVocabulary.HIV_PREVENTION, null, null))
                .isInstanceOf(ConfidentialRegisterException.class);
    }

    @Test
    void tbIsNotRefused() {
        // TB is notifiable, which is the opposite of confidential: a TB register is a public-health
        // instrument. Refusing it would treat notifiability as though it were protection.
        save(ProgrammeVocabulary.TB_TREATMENT, "fac-1");

        Map<String, Object> out = service.register(ProgrammeVocabulary.TB_TREATMENT, "fac-1", null);
        assertThat(out).containsEntry("programme", ProgrammeVocabulary.TB_TREATMENT);
        assertThat(out).containsEntry("total", 1L);
    }

    @Test
    void everyChronicRegisterIsServed() {
        for (String register : ProgrammeVocabulary.CHRONIC_REGISTERS) {
            save(register, "fac-1");
            Map<String, Object> out = service.register(register, "fac-1", null);
            assertThat(out).containsEntry("programme", register);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNeverAssessedEntryIsCountedAndTheResponseSaysWhatNullMeans() {
        // The misreading this prevents: a null control_status rendered as though it were an
        // assessment. It is neither "doing well" nor "doing badly" — nobody has looked.
        save(ProgrammeVocabulary.DIABETES, "fac-1");

        Map<String, Object> out = service.register(ProgrammeVocabulary.DIABETES, "fac-1", null);

        assertThat(out).containsEntry("never_assessed", 1L);
        assertThat((String) out.get("control_status_null_means_never_assessed"))
                .contains("nobody has assessed")
                .contains("not a claim that they are doing badly");
        List<Map<String, Object>> entries = (List<Map<String, Object>>) out.get("entries");
        // The key must be present and null rather than absent, so a consumer cannot confuse "never
        // assessed" with "this API does not report control".
        assertThat(entries.get(0)).containsKey("control_status");
        assertThat(entries.get(0).get("control_status")).isNull();
    }

    @Test
    void anUnknownProgrammeIsRefusedRatherThanReturningAnEmptyRegister() {
        // An empty register for a misspelled programme reads as "nobody is on it", which is the
        // reassuring direction and therefore the dangerous one.
        assertThatThrownBy(() -> service.register("HYPERTENSIN", "fac-1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
