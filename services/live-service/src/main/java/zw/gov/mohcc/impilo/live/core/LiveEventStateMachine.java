package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class LiveEventStateMachine {

    private static final Map<LiveEventStatus, Set<LiveEventStatus>> TRANSITIONS = new EnumMap<>(LiveEventStatus.class);

    static {
        TRANSITIONS.put(LiveEventStatus.DRAFT, EnumSet.of(
                LiveEventStatus.SCHEDULED, LiveEventStatus.CANCELLED));
        TRANSITIONS.put(LiveEventStatus.SCHEDULED, EnumSet.of(
                LiveEventStatus.LIVE, LiveEventStatus.CANCELLED, LiveEventStatus.ARCHIVED));
        TRANSITIONS.put(LiveEventStatus.LIVE, EnumSet.of(
                LiveEventStatus.ENDED, LiveEventStatus.CANCELLED));
        TRANSITIONS.put(LiveEventStatus.ENDED, EnumSet.of(
                LiveEventStatus.PROCESSING_REPLAY, LiveEventStatus.ARCHIVED));
        TRANSITIONS.put(LiveEventStatus.PROCESSING_REPLAY, EnumSet.of(
                LiveEventStatus.PUBLISHED_REPLAY, LiveEventStatus.ARCHIVED));
        TRANSITIONS.put(LiveEventStatus.PUBLISHED_REPLAY, EnumSet.of(LiveEventStatus.ARCHIVED));
        TRANSITIONS.put(LiveEventStatus.CANCELLED, EnumSet.of(LiveEventStatus.ARCHIVED));
        TRANSITIONS.put(LiveEventStatus.ARCHIVED, EnumSet.noneOf(LiveEventStatus.class));
    }

    public LiveEventEntity transition(LiveEventEntity event, LiveEventStatus target, String updatedBy) {
        LiveEventStatus current = LiveEventStatus.valueOf(event.getStatus());
        if (current == target) {
            return event;
        }
        Set<LiveEventStatus> allowed = TRANSITIONS.getOrDefault(current, EnumSet.noneOf(LiveEventStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException("Invalid live event transition " + current + " -> " + target);
        }
        event.setStatus(target.name());
        event.setUpdatedBy(updatedBy);
        return event;
    }

    public boolean canTransition(LiveEventStatus from, LiveEventStatus to) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(LiveEventStatus.class)).contains(to);
    }
}
