package zw.gov.mohcc.impilo.experience.workhome;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.worklist.ClinicalWorklistComposer;
import zw.gov.mohcc.impilo.experience.worklist.WorklistRanking;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.util.List;
import java.util.Map;

/**
 * VIRTUAL_CARE — the same cross-service clinical composer as
 * {@link FacilityClinicalWorkHomeAdapter}, anchored to the virtual pool's host/anchor facility
 * (A4) rather than a physical duty station. A distinct family because a future virtual-specific
 * section (cover-scope summary, host-organisation banner) attaches here without touching the
 * physical-facility adapter.
 */
@Component
public class VirtualCareWorkHomeAdapter implements WorkHomeFamilyAdapter {

    private static final int SECTION_SIZE = 60;

    private final ClinicalWorklistComposer composer;

    public VirtualCareWorkHomeAdapter(ClinicalWorklistComposer composer) {
        this.composer = composer;
    }

    @Override
    public WorkHomeFamily family() {
        return WorkHomeFamily.VIRTUAL_CARE;
    }

    @Override
    public List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context) {
        return List.of(FanoutTask.of("virtual-worklist", () -> compose(context)));
    }

    private WorkHomeSection compose(ResolvedWorkContext context) {
        String facilityId = context.facilityId();
        if (facilityId == null || facilityId.isBlank()) {
            return WorkHomeSection.empty("virtual-worklist", "Virtual care worklist",
                    "No virtual-pool anchor facility resolved for this context");
        }
        List<Map<String, Object>> items = WorklistRanking.sort(composer.composeAll(facilityId, SECTION_SIZE));
        return WorkHomeSection.ok("virtual-worklist", "Virtual care worklist", items, WorklistRanking.summarize(items));
    }
}
