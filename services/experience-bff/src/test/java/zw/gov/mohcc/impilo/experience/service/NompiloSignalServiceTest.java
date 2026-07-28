package zw.gov.mohcc.impilo.experience.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.workhome.ProfessionalAlertsComposer;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeCompositionService;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeResult;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeSection;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NompiloSignalServiceTest {

    private static final String ACTOR_ID = "actor-1";
    private static final String CONTEXT_ID = "ctx-1";

    @Mock
    private WalletOverviewService walletService;
    @Mock
    private WorkHomeCompositionService workHomeCompositionService;

    private NompiloSignalService service() {
        return new NompiloSignalService(walletService, workHomeCompositionService);
    }

    @Test
    void workSignals_friendlyStateSet_returnsContextUnavailableOnly() {
        when(workHomeCompositionService.compose(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE"))
                .thenReturn(WorkHomeResult.unavailable("work_context_unavailable"));

        NompiloSignalService.WorkSignals result = service().workSignals(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE");

        assertThat(result.signals()).containsExactly("work.context_unavailable");
    }

    @Test
    void workSignals_actNowBucketNonEmpty_firesActNowSignal() {
        WorkHomeSection section = WorkHomeSection.ok("clinical-worklist", "Clinical worklist",
                List.of(Map.of("id", "1", "status", "OPEN", "priority", "URGENT")), Map.of("total", 1));
        when(workHomeCompositionService.compose(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE"))
                .thenReturn(new WorkHomeResult(CONTEXT_ID, "FACILITY_CLINICAL", "CLINICAL_CARE", null, List.of(section)));

        NompiloSignalService.WorkSignals result = service().workSignals(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE");

        assertThat(result.signals()).contains("work.act_now_pending", "work.family.facility_clinical");
        assertThat(result.signals()).doesNotContain("work.section_degraded", "work.context_unavailable");
    }

    @Test
    void workSignals_degradedSection_firesSectionDegradedSignal() {
        WorkHomeSection section = WorkHomeSection.degraded("clinical-worklist", "Clinical worklist", "timed out");
        when(workHomeCompositionService.compose(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE"))
                .thenReturn(new WorkHomeResult(CONTEXT_ID, "FACILITY_CLINICAL", "CLINICAL_CARE", null, List.of(section)));

        NompiloSignalService.WorkSignals result = service().workSignals(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE");

        assertThat(result.signals()).contains("work.section_degraded");
    }

    @Test
    void workSignals_professionalAlertsSectionHasItems_firesProfessionalAlertSignal() {
        WorkHomeSection alerts = WorkHomeSection.ok(ProfessionalAlertsComposer.SECTION_ID, "Professional alerts",
                List.of(Map.of("id", "licence:1", "priority", "URGENT", "status", "EXPIRING")), Map.of("total", 1));
        when(workHomeCompositionService.compose(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE"))
                .thenReturn(new WorkHomeResult(CONTEXT_ID, "FACILITY_CLINICAL", "CLINICAL_CARE", null, List.of(alerts)));

        NompiloSignalService.WorkSignals result = service().workSignals(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE");

        assertThat(result.signals()).contains("work.professional_alert_open");
    }

    @Test
    void workSignals_compositionThrows_failsClosedToContextUnavailable() {
        when(workHomeCompositionService.compose(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE"))
                .thenThrow(new RuntimeException("bff composition error"));

        NompiloSignalService.WorkSignals result = service().workSignals(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE");

        assertThat(result.signals()).containsExactly("work.context_unavailable");
    }
}
