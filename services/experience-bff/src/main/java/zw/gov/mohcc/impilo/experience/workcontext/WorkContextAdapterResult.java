package zw.gov.mohcc.impilo.experience.workcontext;

import java.util.List;

/** One source adapter's contribution to the union: its contexts plus its own health status. */
public record WorkContextAdapterResult(List<ResolvedWorkContext> contexts, WorkContextSourceStatus status) {
}
