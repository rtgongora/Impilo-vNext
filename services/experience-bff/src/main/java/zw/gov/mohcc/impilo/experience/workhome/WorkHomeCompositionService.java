package zw.gov.mohcc.impilo.experience.workhome;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;
import zw.gov.mohcc.impilo.experience.workcontext.WorkContextResolutionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composes the Work Home for the caller's active work context (Phase E3): re-proves the
 * context from source exactly like the C3 session mint (never trusts a client-supplied
 * contextId as a bearer credential), selects the single {@link WorkHomeFamilyAdapter} matching
 * the active mode, and fans its sections out via {@link ContextPropagatingFanout}.
 *
 * <p>Always produces a result. Section-level failure degrades in-band ({@link WorkHomeSection});
 * context/mode failure produces the same generic, non-enumerating {@code friendlyState} values
 * the session mint uses ({@code work_context_unavailable} / {@code work_mode_unavailable}) so a
 * denied caller cannot learn which specific reason applied.</p>
 */
@Service
public class WorkHomeCompositionService {

    private final WorkContextResolutionService resolutionService;
    private final ContextPropagatingFanout fanout;
    private final Map<WorkHomeFamily, WorkHomeFamilyAdapter> adaptersByFamily;

    public WorkHomeCompositionService(WorkContextResolutionService resolutionService,
                                       ContextPropagatingFanout fanout,
                                       List<WorkHomeFamilyAdapter> adapters) {
        this.resolutionService = resolutionService;
        this.fanout = fanout;
        Map<WorkHomeFamily, WorkHomeFamilyAdapter> byFamily = new HashMap<>();
        for (WorkHomeFamilyAdapter adapter : adapters) {
            byFamily.put(adapter.family(), adapter);
        }
        this.adaptersByFamily = byFamily;
    }

    /** {@code GET /internal/v1/work-home} — every section for the active context, fat first paint. */
    public WorkHomeResult compose(String actorHealthId, String entryRouteContextKind, String contextId, String requestedMode) {
        Resolved resolved = resolve(actorHealthId, entryRouteContextKind, contextId, requestedMode);
        if (resolved.failure() != null) {
            return resolved.failure();
        }
        List<FanoutTask<WorkHomeSection>> tasks = resolved.adapter().sectionTasks(resolved.context());
        List<FanoutResult<WorkHomeSection>> results = fanout.run(tasks);
        List<WorkHomeSection> sections = results.stream().map(WorkHomeCompositionService::toSection).toList();
        return new WorkHomeResult(resolved.context().contextId(), resolved.adapter().family().name(),
                resolved.mode(), null, sections);
    }

    /** {@code GET /internal/v1/work-home/sections/{id}} — retry/differential polling for one section only. */
    public WorkHomeSection composeSection(String actorHealthId, String entryRouteContextKind, String contextId,
                                           String requestedMode, String sectionId) {
        Resolved resolved = resolve(actorHealthId, entryRouteContextKind, contextId, requestedMode);
        if (resolved.failure() != null) {
            return WorkHomeSection.empty(sectionId, sectionId, resolved.failure().friendlyState());
        }
        List<FanoutTask<WorkHomeSection>> matching = resolved.adapter().sectionTasks(resolved.context()).stream()
                .filter(t -> t.sectionId().equals(sectionId))
                .toList();
        if (matching.isEmpty()) {
            return WorkHomeSection.empty(sectionId, sectionId, "Unknown section for the active work context family");
        }
        List<FanoutResult<WorkHomeSection>> results = fanout.run(matching);
        return toSection(results.get(0));
    }

    private Resolved resolve(String actorHealthId, String entryRouteContextKind, String contextId, String requestedMode) {
        ResolvedWorkContext context = resolutionService.proveContext(actorHealthId, entryRouteContextKind, contextId);
        if (context == null) {
            return Resolved.failed(WorkHomeResult.unavailable("work_context_unavailable"));
        }
        String mode = requestedMode;
        if (mode == null || mode.isBlank() || !context.availableModes().contains(mode)) {
            mode = context.defaultMode();
        }
        WorkHomeFamily family = WorkHomeFamily.forMode(mode);
        WorkHomeFamilyAdapter adapter = family != null ? adaptersByFamily.get(family) : null;
        if (adapter == null) {
            return Resolved.failed(WorkHomeResult.unavailable("work_mode_unavailable"));
        }
        return Resolved.ok(context, mode, adapter);
    }

    private static WorkHomeSection toSection(FanoutResult<WorkHomeSection> result) {
        return switch (result.status()) {
            case OK -> result.value();
            case TIMEOUT -> WorkHomeSection.degraded(result.sectionId(), result.sectionId(), "section timed out");
            case ERROR -> WorkHomeSection.degraded(result.sectionId(), result.sectionId(), result.errorMessage());
        };
    }

    private record Resolved(ResolvedWorkContext context, String mode, WorkHomeFamilyAdapter adapter, WorkHomeResult failure) {
        static Resolved ok(ResolvedWorkContext context, String mode, WorkHomeFamilyAdapter adapter) {
            return new Resolved(context, mode, adapter, null);
        }

        static Resolved failed(WorkHomeResult failure) {
            return new Resolved(null, null, null, failure);
        }
    }
}
