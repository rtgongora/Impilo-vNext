package zw.gov.mohcc.impilo.sessiontemplates;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One session mode's doctrine document (contracts/schemas/session-templates).
 * Immutable; loaded and validated by {@link SessionTemplateRegistry} at boot.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SessionTemplate(
        SessionMode sessionMode,
        String owningService,
        List<String> liveModes,
        Map<String, TokenGrantProfile> roles,
        String defaultLayout,
        String joinFlow,
        String lobbyBehaviour,
        int tokenTtlSeconds,
        int maxParticipants,
        String roomPrefix,
        RecordingRule recording,
        ChatRule chat,
        boolean moderationRequired,
        List<String> screenShareRoles,
        boolean resourceSharing,
        String auditDepth,
        TelemetryRule telemetry,
        List<String> postSessionActions,
        List<String> khulumaNotificationKeys,
        String mobileParity,
        FallbackRules fallbackRules,
        String completionCriteriaRef
) {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TokenGrantProfile(
            boolean canPublish,
            boolean canSubscribe,
            boolean canPublishData,
            Boolean roomAdmin,
            Boolean hidden,
            Boolean audioOnlyDefault
    ) {
        public boolean isRoomAdmin() {
            return Boolean.TRUE.equals(roomAdmin);
        }

        public boolean isHidden() {
            return Boolean.TRUE.equals(hidden);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RecordingRule(
            boolean defaultOn,
            List<String> whoCanStart,
            boolean consentRequired,
            String sensitivityClass,
            String artifactOwner
    ) {
        public boolean canStart(String role) {
            return role != null && whoCanStart != null
                    && whoCanStart.stream().anyMatch(r -> r.equalsIgnoreCase(role));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ChatRule(boolean enabled, boolean moderated, String persistence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TelemetryRule(boolean participantStats, Boolean qualityEvents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record FallbackRules(boolean audioFirstAllowed, int reconnectGraceSeconds, Boolean sipFallback) {
    }

    /** Grant profile for a role, or null when the role is not allowed in this mode. */
    public TokenGrantProfile grantFor(String role) {
        if (role == null || roles == null) {
            return null;
        }
        return roles.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(role))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /** Room name prefix; defaults to impilo-<mode> when the document omits it. */
    public String effectiveRoomPrefix() {
        if (roomPrefix != null && !roomPrefix.isBlank()) {
            return roomPrefix;
        }
        return "impilo-" + sessionMode.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
