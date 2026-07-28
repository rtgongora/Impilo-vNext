package zw.gov.mohcc.impilo.experience.workhome;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.worklist.ClinicalWorklistComposer;
import zw.gov.mohcc.impilo.experience.worklist.WorklistRanking;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.util.List;
import java.util.Map;

/** CLINICAL_CARE — the composed cross-service worklist (Phase E1), unchanged shape. */
@Component
public class FacilityClinicalWorkHomeAdapter implements WorkHomeFamilyAdapter {

    private static final int SECTION_SIZE = 60;

    private final ClinicalWorklistComposer composer;

    public FacilityClinicalWorkHomeAdapter(ClinicalWorklistComposer composer) {
        this.composer = composer;
    }

    @Override
    public WorkHomeFamily family() {
        return WorkHomeFamily.FACILITY_CLINICAL;
    }

    @Override
    public List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context) {
        return List.of(FanoutTask.of("clinical-worklist", () -> compose(context)));
    }

    private WorkHomeSection compose(ResolvedWorkContext context) {
        String facilityId = context.facilityId();
        if (facilityId == null || facilityId.isBlank()) {
            return WorkHomeSection.empty("clinical-worklist", "Clinical worklist",
                    "No facility anchor resolved for this context");
        }
        List<Map<String, Object>> items = WorklistRanking.sort(composer.composeAll(facilityId, SECTION_SIZE));
        return WorkHomeSection.ok("clinical-worklist", "Clinical worklist", items, WorklistRanking.summarize(items));
    }
}
