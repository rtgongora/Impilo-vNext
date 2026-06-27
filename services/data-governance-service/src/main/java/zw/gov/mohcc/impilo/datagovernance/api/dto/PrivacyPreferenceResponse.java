package zw.gov.mohcc.impilo.datagovernance.api.dto;

import zw.gov.mohcc.impilo.datagovernance.domain.PrivacyDisplayPreferenceEntity;

/** Canonical view of a user's privacy display preference. */
public record PrivacyPreferenceResponse(
        String userId,
        String defaultLevel,
        int autoLockMinutes,
        boolean screenShareMode,
        boolean persisted,
        String updatedAt
) {
    public static PrivacyPreferenceResponse from(PrivacyDisplayPreferenceEntity e) {
        return new PrivacyPreferenceResponse(
                e.getUserId(),
                e.getDefaultLevel(),
                e.getAutoLockMinutes(),
                e.isScreenShareMode(),
                true,
                e.getUpdatedAt().toString());
    }
}
