package zw.gov.mohcc.impilo.rtc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rtc")
public class RtcGatewayProperties {
    private Gateway gateway = new Gateway();
    private Livekit livekit = new Livekit();
    private Webhook webhook = new Webhook();
    private Recording recording = new Recording();

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

    public Webhook getWebhook() {
        return webhook;
    }

    public void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }

    public Recording getRecording() {
        return recording;
    }

    public void setRecording(Recording recording) {
        this.recording = recording;
    }

    /**
     * Recording (egress) artifact target. LiveKit room-composite egress needs the
     * S3 destination on every StartRoomCompositeEgress request; preview wires the
     * in-chart MinIO here (RTC_RECORDING_S3_* / RTC_RECORDING_BUCKET env).
     */
    public static class Recording {
        private S3 s3 = new S3();

        public S3 getS3() {
            return s3;
        }

        public void setS3(S3 s3) {
            this.s3 = s3;
        }

        public static class S3 {
            private String accessKey = "";
            private String secretKey = "";
            private String endpoint = "";
            private String bucket = "impilo-recordings";
            private String region = "us-east-1";
            private boolean forcePathStyle = true;

            public String getAccessKey() {
                return accessKey;
            }

            public void setAccessKey(String accessKey) {
                this.accessKey = accessKey;
            }

            public String getSecretKey() {
                return secretKey;
            }

            public void setSecretKey(String secretKey) {
                this.secretKey = secretKey;
            }

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getBucket() {
                return bucket;
            }

            public void setBucket(String bucket) {
                this.bucket = bucket;
            }

            public String getRegion() {
                return region;
            }

            public void setRegion(String region) {
                this.region = region;
            }

            public boolean isForcePathStyle() {
                return forcePathStyle;
            }

            public void setForcePathStyle(boolean forcePathStyle) {
                this.forcePathStyle = forcePathStyle;
            }
        }
    }

    public static class Webhook {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Gateway {
        private String provider = "LIVEKIT";
        private boolean failClosed = true;
        private boolean devModeEnabled = false;
        private long tokenTtlSeconds = 3600;
        private String roomPrefix = "impilo-telemedicine";
        private boolean recordingEnabled = false;
        private boolean egressEnabled = false;
        /**
         * RTMP stream-out (room-composite egress with stream outputs). OFF by
         * default: the estate has no restream target infrastructure yet — the
         * API is landed behind this flag as an honest seam.
         */
        private boolean streamOutEnabled = false;
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

        public boolean isStreamOutEnabled() {
            return streamOutEnabled;
        }

        public void setStreamOutEnabled(boolean streamOutEnabled) {
            this.streamOutEnabled = streamOutEnabled;
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
        private String startRoomCompositeEgressPath = "/twirp/livekit.Egress/StartRoomCompositeEgress";
        private String stopEgressPath = "/twirp/livekit.Egress/StopEgress";
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

        public String getStartRoomCompositeEgressPath() {
            return startRoomCompositeEgressPath;
        }

        public void setStartRoomCompositeEgressPath(String startRoomCompositeEgressPath) {
            this.startRoomCompositeEgressPath = startRoomCompositeEgressPath;
        }

        public String getStopEgressPath() {
            return stopEgressPath;
        }

        public void setStopEgressPath(String stopEgressPath) {
            this.stopEgressPath = stopEgressPath;
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
