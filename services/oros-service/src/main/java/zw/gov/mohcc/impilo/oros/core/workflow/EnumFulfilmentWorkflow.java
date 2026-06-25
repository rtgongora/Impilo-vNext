package zw.gov.mohcc.impilo.oros.core.workflow;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base for an enum-backed {@link FulfilmentWorkflow}. Subclasses supply the state enum, the
 * entry state, and the deny-by-default transition map; this base handles the {@code String}↔enum
 * marshalling so the guard speaks the persisted state names.
 *
 * @param <S> the category's fine-grained state enum
 */
public abstract class EnumFulfilmentWorkflow<S extends Enum<S>> implements FulfilmentWorkflow {

    private final Class<S> stateType;

    protected EnumFulfilmentWorkflow(Class<S> stateType) {
        this.stateType = stateType;
    }

    /** The deny-by-default transition graph; terminal states map to an empty set. */
    protected abstract Map<S, Set<S>> transitions();

    /** The entry state for a fresh order of this category. */
    protected abstract S entry();

    @Override
    public String entryState() {
        return entry().name();
    }

    @Override
    public Set<String> states() {
        return Arrays.stream(stateType.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    @Override
    public boolean canTransition(String from, String to) {
        S target = parse(to);
        if (target == null) {
            return false;
        }
        if (from == null) {
            return target == entry();
        }
        S source = parse(from);
        if (source == null) {
            return false;
        }
        return transitions().getOrDefault(source, Set.of()).contains(target);
    }

    @Override
    public Set<String> allowedFrom(String from) {
        if (from == null) {
            return Set.of(entry().name());
        }
        S source = parse(from);
        if (source == null) {
            return Set.of();
        }
        return transitions().getOrDefault(source, Set.of()).stream()
                .map(Enum::name).collect(Collectors.toSet());
    }

    /** Parse a state name to its enum constant; returns {@code null} for unknown names. */
    private S parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(stateType, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
