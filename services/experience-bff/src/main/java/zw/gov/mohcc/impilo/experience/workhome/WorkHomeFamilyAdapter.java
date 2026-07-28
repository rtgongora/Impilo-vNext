package zw.gov.mohcc.impilo.experience.workhome;

import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.util.List;

/**
 * One Work Home family's section set (Phase E4). {@link WorkHomeCompositionService} picks the
 * single adapter whose {@link #family()} matches the active context's mode and fans its
 * {@link #sectionTasks} out concurrently via {@link ContextPropagatingFanout} — branching
 * between what a clinician, a facility manager, a regulator and a support engineer see lives
 * here, one adapter per family, never as conditionals in the composition service or the page.
 */
public interface WorkHomeFamilyAdapter {

    WorkHomeFamily family();

    /**
     * One {@link zw.gov.mohcc.impilo.experience.workhome.FanoutTask} per section this family
     * shows. Each supplier must itself fail closed to an honest {@link WorkHomeSection#empty}
     * or throw (the fan-out converts a thrown exception to {@code DEGRADED}) — never fabricate
     * counts when a downstream is unavailable or a deeper workflow does not exist yet.
     */
    List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context);
}
