package zw.gov.mohcc.impilo.experience.telemedicine;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Component
public class TelemedicineCommsMetricsStore {

    private final LongAdder remindersSent = new LongAdder();
    private final LongAdder joinInstructionsSent = new LongAdder();
    private final LongAdder joinSuccessEvents = new LongAdder();
    private final LongAdder joinSupportRequests = new LongAdder();
    private final LongAdder noShowNudgesSent = new LongAdder();
    private final LongAdder fallbackEvents = new LongAdder();
    private final LongAdder followupsSent = new LongAdder();
    private final LongAdder surveysCompleted = new LongAdder();
    private final LongAdder escalationToAgent = new LongAdder();
    private final LongAdder escalationToProvider = new LongAdder();
    private final LongAdder clientUnreachable = new LongAdder();
    private final LongAdder providerDelayNotifications = new LongAdder();
    private final LongAdder supportResponseTimeMsSum = new LongAdder();
    private final LongAdder supportResponseTimeMsCount = new LongAdder();

    public void incrementRemindersSent() { remindersSent.increment(); }
    public void incrementJoinInstructionsSent() { joinInstructionsSent.increment(); }
    public void incrementJoinSuccessEvents() { joinSuccessEvents.increment(); }
    public void incrementJoinSupportRequests() { joinSupportRequests.increment(); }
    public void incrementNoShowNudgesSent() { noShowNudgesSent.increment(); }
    public void incrementFallbackEvents() { fallbackEvents.increment(); }
    public void incrementFollowupsSent() { followupsSent.increment(); }
    public void incrementSurveysCompleted() { surveysCompleted.increment(); }
    public void incrementEscalationToAgent() { escalationToAgent.increment(); }
    public void incrementEscalationToProvider() { escalationToProvider.increment(); }
    public void incrementClientUnreachable() { clientUnreachable.increment(); }
    public void incrementProviderDelayNotifications() { providerDelayNotifications.increment(); }

    public void recordSupportResponseTimeMs(long durationMs) {
        if (durationMs < 0) {
            return;
        }
        supportResponseTimeMsSum.add(durationMs);
        supportResponseTimeMsCount.increment();
    }

    public Map<String, Object> snapshot() {
        long reminders = remindersSent.sum();
        long joinInstructions = joinInstructionsSent.sum();
        long joinSuccess = joinSuccessEvents.sum();
        long joinSupport = joinSupportRequests.sum();
        long noShow = noShowNudgesSent.sum();
        long fallback = fallbackEvents.sum();
        long followups = followupsSent.sum();
        long surveys = surveysCompleted.sum();
        long escalatedAgent = escalationToAgent.sum();
        long escalatedProvider = escalationToProvider.sum();
        long unreachable = clientUnreachable.sum();
        long delayNotifications = providerDelayNotifications.sum();
        long supportCount = supportResponseTimeMsCount.sum();
        double avgSupportMs = supportCount == 0 ? 0D : (double) supportResponseTimeMsSum.sum() / (double) supportCount;

        double reminderToAttendanceConversion = reminders == 0 ? 0D : (double) joinSuccess / (double) reminders;
        double joinSuccessRate = joinInstructions == 0 ? 0D : (double) joinSuccess / (double) joinInstructions;
        double surveyResponseRate = followups == 0 ? 0D : (double) surveys / (double) followups;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("telemedicine_reminders_sent", reminders);
        out.put("join_success_rate", joinSuccessRate);
        out.put("failed_join_support_requests", joinSupport);
        out.put("no_show_nudges_sent", noShow);
        out.put("reminder_to_attendance_conversion", reminderToAttendanceConversion);
        out.put("video_audio_fallback_events", fallback);
        out.put("post_consultation_followup_completion", followups);
        out.put("survey_response_rate", surveyResponseRate);
        out.put("escalation_volumes", escalatedAgent + escalatedProvider);
        out.put("client_unreachable_counts", unreachable);
        out.put("provider_delay_notifications", delayNotifications);
        out.put("average_telemedicine_support_response_ms", avgSupportMs);
        return out;
    }
}
