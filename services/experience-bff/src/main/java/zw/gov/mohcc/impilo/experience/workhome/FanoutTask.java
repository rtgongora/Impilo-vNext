package zw.gov.mohcc.impilo.experience.workhome;

import java.util.function.Supplier;

/**
 * One unit of work for {@link ContextPropagatingFanout} — a labelled supplier so a
 * degraded/timed-out result can still be attributed to its Work Home section.
 */
public record FanoutTask<T>(String sectionId, Supplier<T> supplier) {

    public static <T> FanoutTask<T> of(String sectionId, Supplier<T> supplier) {
        return new FanoutTask<>(sectionId, supplier);
    }
}
