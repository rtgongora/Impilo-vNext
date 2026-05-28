package zw.gov.mohcc.impilo.rtc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rtc")
public class RtcGatewayProperties {
    private Gateway gateway = new Gateway();
    private Livekit livekit = new Livekit();

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public Livekit getLivekit() {
        return livekit;
    }

    public void setLivekit(Livekit livekit) {
        this.livekit = livekit;
    }

    public static class Gateway {
        private String provider = "LIVEKIT";
        private boolean failClosed = true;
        private boolean devModeEnabled = false;
        private long tokenTtlSeconds = 3600;
        private String roomPrefix = "impilo-telemedicine";
        private boolean recordingEnabled = false;
        private boolean egressEnabled = false;
        private boolean requireConsentReferenceForMedia = true;
        private boolean allowEmergencyWithoutConsent = true;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public boolean isFailClosed() {
            return failClosed;
        }

        public void setFailClosed(boolean failClosed) {
            this.failClosed = failClosed;
        }

        public boolean isDevModeEnabled() {
            return devModeEnabled;
        }

        public void setDevModeEnabled(boolean devModeEnabled) {
            this.devModeEnabled = devModeEnabled;
        }

        public long getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

        public void setTokenTtlSeconds(long tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }

        public String getRoomPrefix() {
            return roomPrefix;
        }

        public void setRoomPrefix(String roomPrefix) {
            this.roomPrefix = roomPrefix;
        }

        public boolean isRecordingEnabled() {
            return recordingEnabled;
        }

        public void setRecordingEnabled(boolean recordingEnabled) {
            this.recordingEnabled = recordingEnabled;
        }

        public boolean isEgressEnabled() {
            return egressEnabled;
        }

        public void setEgressEnabled(boolean egressEnabled) {
            this.egressEnabled = egressEnabled;
        }

        public boolean isRequireConsentReferenceForMedia() {
            return requireConsentReferenceForMedia;
        }

        public void setRequireConsentReferenceForMedia(boolean requireConsentReferenceForMedia) {
            this.requireConsentReferenceForMedia = requireConsentReferenceForMedia;
        }

        public boolean isAllowEmergencyWithoutConsent() {
            return allowEmergencyWithoutConsent;
        }

        public void setAllowEmergencyWithoutConsent(boolean allowEmergencyWithoutConsent) {
            this.allowEmergencyWithoutConsent = allowEmergencyWithoutConsent;
        }
    }

    public static class Livekit {
        private boolean enabled = false;
        private String url = "";
        private String clientUrl = "";
        private String apiKey = "";
        private String apiSecret = "";
        private String createRoomPath = "/twirp/livekit.RoomService/CreateRoom";
        private String deleteRoomPath = "/twirp/livekit.RoomService/DeleteRoom";
        private int defaultEmptyTimeoutSeconds = 900;
        private int maxParticipants = 8;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getClientUrl() {
            return clientUrl;
        }

        public void setClientUrl(String clientUrl) {
            this.clientUrl = clientUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public String getCreateRoomPath() {
            return createRoomPath;
        }

        public void setCreateRoomPath(String createRoomPath) {
            this.createRoomPath = createRoomPath;
        }

        public String getDeleteRoomPath() {
            return deleteRoomPath;
        }

        public void setDeleteRoomPath(String deleteRoomPath) {
            this.deleteRoomPath = deleteRoomPath;
        }

        public int getDefaultEmptyTimeoutSeconds() {
            return defaultEmptyTimeoutSeconds;
        }

        public void setDefaultEmptyTimeoutSeconds(int defaultEmptyTimeoutSeconds) {
            this.defaultEmptyTimeoutSeconds = defaultEmptyTimeoutSeconds;
        }

        public int getMaxParticipants() {
            return maxParticipants;
        }

        public void setMaxParticipants(int maxParticipants) {
            this.maxParticipants = maxParticipants;
        }
    }
}
