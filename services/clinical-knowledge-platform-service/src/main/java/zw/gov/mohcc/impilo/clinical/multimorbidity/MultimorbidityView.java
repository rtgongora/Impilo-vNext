package zw.gov.mohcc.impilo.clinical.multimorbidity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The multimorbidity view required by brief.md §9 — eleven panels and seven detections.
 *
 * <p><strong>Every panel and every detection carries its own state.</strong> A view assembled from
 * six sources will routinely have four of them and it must say which four. The alternative — a panel
 * that renders empty when its source was unreachable — turns "we could not look" into "there is
 * nothing there", and does so on the one screen a clinician opens specifically to find what the
 * single-disease screens hid from them.</p>
 */
public record MultimorbidityView(
        List<Panel> panels,
        List<Detection> detections,
        String contentVersion,
        String approvalStatus,
        /** Null when everything could be assessed; otherwise names what could not be, and why. */
        String completenessNote) {

    /** Whether a panel could be populated at all. */
    public enum PanelState {
        /** The source was read and its contents are below. */
        KNOWN,
        /** The source could not be read. The panel is empty because we did not look, not because it is. */
        UNKNOWN
    }

    /** The outcome of one of §9's seven detections. */
    public enum DetectionState {
        /** Something was found. */
        DETECTED,
        /** Every input was available and nothing was found. This is the only reassuring value. */
        NOT_DETECTED,
        /** An input was missing, so the question was not answered. Never reassuring. */
        UNDETERMINED
    }

    public record Panel(String key, String title, PanelState state, List<String> entries, String unknownReason) {

        static Panel known(String key, String title, List<String> entries) {
            return new Panel(key, title, PanelState.KNOWN, List.copyOf(entries), null);
        }

        static Panel unknown(String key, String title, String reason) {
            return new Panel(key, title, PanelState.UNKNOWN, List.of(), reason);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("title", title);
            m.put("state", state.name());
            m.put("entries", entries);
            if (unknownReason != null) {
                m.put("unknown_reason", unknownReason);
            }
            return m;
        }
    }

    public record Finding(String code, String severity, String message, String explanation, String requiredAction) {

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("severity", severity);
            m.put("message", message);
            m.put("explanation", explanation);
            m.put("required_action", requiredAction);
            return m;
        }
    }

    public record Detection(String key, String title, DetectionState state, List<Finding> findings,
                            String undeterminedReason) {

        static Detection detected(String key, String title, List<Finding> findings) {
            return new Detection(key, title, DetectionState.DETECTED, List.copyOf(findings), null);
        }

        static Detection clear(String key, String title) {
            return new Detection(key, title, DetectionState.NOT_DETECTED, List.of(), null);
        }

        static Detection undetermined(String key, String title, String reason) {
            return new Detection(key, title, DetectionState.UNDETERMINED, List.of(), reason);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("title", title);
            m.put("state", state.name());
            m.put("findings", findings.stream().map(Finding::toMap).toList());
            if (undeterminedReason != null) {
                m.put("undetermined_reason", undeterminedReason);
            }
            return m;
        }
    }

    /** True when any detection could not be answered — so the view must not read as an all-clear. */
    public boolean incomplete() {
        return detections.stream().anyMatch(d -> d.state() == DetectionState.UNDETERMINED)
               || panels.stream().anyMatch(p -> p.state() == PanelState.UNKNOWN);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("panels", panels.stream().map(Panel::toMap).toList());
        m.put("detections", detections.stream().map(Detection::toMap).toList());
        m.put("incomplete", incomplete());
        m.put("content_version", contentVersion);
        m.put("approval_status", approvalStatus);
        if (completenessNote != null) {
            m.put("completeness_note", completenessNote);
        }
        return m;
    }
}
