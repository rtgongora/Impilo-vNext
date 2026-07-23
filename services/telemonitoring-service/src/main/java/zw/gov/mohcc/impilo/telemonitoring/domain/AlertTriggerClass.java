package zw.gov.mohcc.impilo.telemonitoring.domain;

/**
 * Trigger classes implemented by the OF-B26 engine — an honest subset of the fourteen
 * §14.6 trigger types. The class is explicit on the episode because the correct
 * responder differs (clinical desk vs device ops, §14.6).
 *
 * <ul>
 *   <li>{@link #THRESHOLD_BREACH} — §14.6 triggers 1/2 (absolute + personalised bands
 *       from the plan's current threshold-profile version).</li>
 *   <li>{@link #REPEAT_ABNORMAL} — trigger 5: N breaches inside the configured window
 *       escalate the live episode (one clinical situation = one episode — the class
 *       flips, no sibling spawns).</li>
 *   <li>{@link #DEVICE_OFFLINE} — trigger 8: assigned device silent beyond heartbeat
 *       tolerance (journey #65), raised by the scheduled sweep.</li>
 *   <li>{@link #DEVICE_QUALITY} — trigger 9: persistent DEGRADED/QUARANTINED stamps
 *       from one device; §14.6 storm rule — at most ONE live device-quality episode
 *       per device, storms attach.</li>
 * </ul>
 *
 * <p>Deferred (no substrate yet, reported honestly): trend (3), rate-of-change (4),
 * symptom+measurement (6), missing-scheduled-reading (7 — needs per-plan observation
 * schedules), non-adherence (10 — needs adherence devices), missed-visit (11 — OF-B23),
 * failed-contact (12 — notification channels), clinician/programme free-form rules
 * (13/14 — rules-authoring surface).</p>
 */
public enum AlertTriggerClass {
    THRESHOLD_BREACH,
    REPEAT_ABNORMAL,
    DEVICE_OFFLINE,
    DEVICE_QUALITY;

    /** Clinical episodes group per-plan (one live clinical situation per plan, §14.6). */
    public boolean isClinical() {
        return this == THRESHOLD_BREACH || this == REPEAT_ABNORMAL;
    }
}
