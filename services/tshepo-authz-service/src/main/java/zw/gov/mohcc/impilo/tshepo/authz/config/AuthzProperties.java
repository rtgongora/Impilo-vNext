package zw.gov.mohcc.impilo.tshepo.authz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Externalized configuration for TSHEPO Authorization service.
 * Bound from {@code tshepo.authz.*} in application.yml.
 */
@ConfigurationProperties(prefix = "tshepo.authz")
public class AuthzProperties {

    private RateLimit rateLimit = new RateLimit();
    private int stepUpWindowSeconds = 300;
    private int maxStepUpAttempts = 5;
    private List<String> stepUpMethods = List.of("MFA", "BIOMETRIC", "SUPERVISOR_APPROVAL");
    private int breakGlassTtlMinutes = 60;
    private int breakGlassReviewSlaHours = 24;
    private RiskThresholds riskThresholds = new RiskThresholds();
    private String consentServiceUrl = "http://localhost:8182";
    private String consentEvaluatePath = "/v1/consent/evaluate";
    private String identityServiceUrl = "http://localhost:8181";
    private String auditServiceUrl = "http://localhost:8183";
    private String auditKafkaTopic = "tshepo.audit.events";
    private String keysServiceUrl = "http://localhost:8184";
    private String notificationServiceUrl = "http://localhost:8200";
    private String notificationNotifyPath = "/internal/v1/notify";
    private OtpDelivery otpDelivery = new OtpDelivery();
    private Totp totp = new Totp();
    private ESignet esignet = new ESignet();
    private Cache cache = new Cache();
    private int visibilityEscalationGrantTtlHours = 4;
    private List<String> visibilityEscalationApproverRoles = List.of(
            "SYSTEM_ADMIN", "DEVELOPER", "REGULATORY_REVIEWER");
    private List<String> trustAuthorityAdminRoles = List.of(
            "SYSTEM_ADMIN", "TRUST_ADMIN");
    // Roles whose holders may approve a SUPERVISOR_APPROVAL step-up challenge (dual control).
    private List<String> stepUpSupervisorRoles = List.of(
            "SUPERVISOR", "CLINICAL_SUPERVISOR", "DISTRICT_SUPERVISOR");

    // ── Inner configuration classes ────────────────────────────────────

    public static class RateLimit {
        private int maxRequestsPerMinute = 600;
        private int burstSize = 50;

        public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
        public void setMaxRequestsPerMinute(int maxRequestsPerMinute) { this.maxRequestsPerMinute = maxRequestsPerMinute; }
        public int getBurstSize() { return burstSize; }
        public void setBurstSize(int burstSize) { this.burstSize = burstSize; }
    }

    public static class RiskThresholds {
        private int stepUpTrigger = 61;
        private int denyThreshold = 81;
        private int newDeviceScore = 50;
        private int unknownDeviceScore = 70;
        private int blockedDeviceScore = 100;

        public int getStepUpTrigger() { return stepUpTrigger; }
        public void setStepUpTrigger(int stepUpTrigger) { this.stepUpTrigger = stepUpTrigger; }
        public int getDenyThreshold() { return denyThreshold; }
        public void setDenyThreshold(int denyThreshold) { this.denyThreshold = denyThreshold; }
        public int getNewDeviceScore() { return newDeviceScore; }
        public void setNewDeviceScore(int newDeviceScore) { this.newDeviceScore = newDeviceScore; }
        public int getUnknownDeviceScore() { return unknownDeviceScore; }
        public void setUnknownDeviceScore(int unknownDeviceScore) { this.unknownDeviceScore = unknownDeviceScore; }
        public int getBlockedDeviceScore() { return blockedDeviceScore; }
        public void setBlockedDeviceScore(int blockedDeviceScore) { this.blockedDeviceScore = blockedDeviceScore; }
    }

    /**
     * SMS-OTP step-up delivery via the notification service (the comms system-of-record).
     * Opt-in: when {@code notificationEnabled=false} (default) the fail-closed default
     * {@code OtpDeliveryAdapter} stays in effect, so SMS-OTP cannot complete. Enable only in
     * environments where the notification service has a real delivery gateway configured —
     * actual SMS delivery + recipient contact resolution remain the notification service's
     * responsibility (and external dependency); authz only hands off the OTP.
     */
    public static class OtpDelivery {
        private boolean notificationEnabled = false;
        private String channel = "SMS";
        private String templateKey = "STEP_UP_OTP";

        public boolean isNotificationEnabled() { return notificationEnabled; }
        public void setNotificationEnabled(boolean notificationEnabled) { this.notificationEnabled = notificationEnabled; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getTemplateKey() { return templateKey; }
        public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    }

    /**
     * Authenticator-app TOTP enrolment (RFC 6238). Opt-in: when {@code enrolmentEnabled=false}
     * (default) the fail-closed default {@code TotpSecretProvider} stays in effect. When enabled,
     * {@code encryptionKey} (a strong deployment secret, ≥32 chars) is required to encrypt enrolled
     * secrets at rest — the service refuses to provide a real provider without it.
     */
    public static class Totp {
        private boolean enrolmentEnabled = false;
        private String encryptionKey = "";
        private String issuer = "Impilo";
        private int digits = 6;
        private int period = 30;

        public boolean isEnrolmentEnabled() { return enrolmentEnabled; }
        public void setEnrolmentEnabled(boolean enrolmentEnabled) { this.enrolmentEnabled = enrolmentEnabled; }
        public String getEncryptionKey() { return encryptionKey; }
        public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public int getDigits() { return digits; }
        public void setDigits(int digits) { this.digits = digits; }
        public int getPeriod() { return period; }
        public void setPeriod(int period) { this.period = period; }
    }

    public static class ESignet {
        private boolean enabled = false;
        private String issuerUri = "http://localhost:8088";
        private String introspectionUri = "http://localhost:8088/oauth/introspect";
        private String clientId = "impilo";
        private String clientSecret = "changeme";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getIssuerUri() { return issuerUri; }
        public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
        public String getIntrospectionUri() { return introspectionUri; }
        public void setIntrospectionUri(String introspectionUri) { this.introspectionUri = introspectionUri; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }

    public static class Cache {
        private int policyRulesTtlSeconds = 300;
        private int deviceProfileTtlSeconds = 600;
        private int sessionTtlSeconds = 1800;

        public int getPolicyRulesTtlSeconds() { return policyRulesTtlSeconds; }
        public void setPolicyRulesTtlSeconds(int policyRulesTtlSeconds) { this.policyRulesTtlSeconds = policyRulesTtlSeconds; }
        public int getDeviceProfileTtlSeconds() { return deviceProfileTtlSeconds; }
        public void setDeviceProfileTtlSeconds(int deviceProfileTtlSeconds) { this.deviceProfileTtlSeconds = deviceProfileTtlSeconds; }
        public int getSessionTtlSeconds() { return sessionTtlSeconds; }
        public void setSessionTtlSeconds(int sessionTtlSeconds) { this.sessionTtlSeconds = sessionTtlSeconds; }
    }

    // ── Top-level getters/setters ──────────────────────────────────────

    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    public int getStepUpWindowSeconds() { return stepUpWindowSeconds; }
    public void setStepUpWindowSeconds(int stepUpWindowSeconds) { this.stepUpWindowSeconds = stepUpWindowSeconds; }

    public int getMaxStepUpAttempts() { return maxStepUpAttempts; }
    public void setMaxStepUpAttempts(int maxStepUpAttempts) { this.maxStepUpAttempts = maxStepUpAttempts; }
    public List<String> getStepUpMethods() { return stepUpMethods; }
    public void setStepUpMethods(List<String> stepUpMethods) { this.stepUpMethods = stepUpMethods; }
    public int getBreakGlassTtlMinutes() { return breakGlassTtlMinutes; }
    public void setBreakGlassTtlMinutes(int breakGlassTtlMinutes) { this.breakGlassTtlMinutes = breakGlassTtlMinutes; }
    public int getBreakGlassReviewSlaHours() { return breakGlassReviewSlaHours; }
    public void setBreakGlassReviewSlaHours(int breakGlassReviewSlaHours) { this.breakGlassReviewSlaHours = breakGlassReviewSlaHours; }
    public RiskThresholds getRiskThresholds() { return riskThresholds; }
    public void setRiskThresholds(RiskThresholds riskThresholds) { this.riskThresholds = riskThresholds; }
    public String getConsentServiceUrl() { return consentServiceUrl; }
    public void setConsentServiceUrl(String consentServiceUrl) { this.consentServiceUrl = consentServiceUrl; }
    public String getConsentEvaluatePath() { return consentEvaluatePath; }
    public void setConsentEvaluatePath(String consentEvaluatePath) { this.consentEvaluatePath = consentEvaluatePath; }
    public String getIdentityServiceUrl() { return identityServiceUrl; }
    public void setIdentityServiceUrl(String identityServiceUrl) { this.identityServiceUrl = identityServiceUrl; }
    public String getAuditServiceUrl() { return auditServiceUrl; }
    public void setAuditServiceUrl(String auditServiceUrl) { this.auditServiceUrl = auditServiceUrl; }
    public String getAuditKafkaTopic() { return auditKafkaTopic; }
    public void setAuditKafkaTopic(String auditKafkaTopic) { this.auditKafkaTopic = auditKafkaTopic; }
    public String getKeysServiceUrl() { return keysServiceUrl; }
    public void setKeysServiceUrl(String keysServiceUrl) { this.keysServiceUrl = keysServiceUrl; }
    public String getNotificationServiceUrl() { return notificationServiceUrl; }
    public void setNotificationServiceUrl(String notificationServiceUrl) { this.notificationServiceUrl = notificationServiceUrl; }
    public String getNotificationNotifyPath() { return notificationNotifyPath; }
    public void setNotificationNotifyPath(String notificationNotifyPath) { this.notificationNotifyPath = notificationNotifyPath; }
    public OtpDelivery getOtpDelivery() { return otpDelivery; }
    public void setOtpDelivery(OtpDelivery otpDelivery) { this.otpDelivery = otpDelivery; }
    public Totp getTotp() { return totp; }
    public void setTotp(Totp totp) { this.totp = totp; }
    public ESignet getEsignet() { return esignet; }
    public void setEsignet(ESignet esignet) { this.esignet = esignet; }
    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }

    public int getVisibilityEscalationGrantTtlHours() {
        return visibilityEscalationGrantTtlHours;
    }

    public void setVisibilityEscalationGrantTtlHours(int visibilityEscalationGrantTtlHours) {
        this.visibilityEscalationGrantTtlHours = visibilityEscalationGrantTtlHours;
    }

    public List<String> getVisibilityEscalationApproverRoles() {
        return visibilityEscalationApproverRoles;
    }

    public void setVisibilityEscalationApproverRoles(List<String> visibilityEscalationApproverRoles) {
        this.visibilityEscalationApproverRoles = visibilityEscalationApproverRoles;
    }

    public List<String> getTrustAuthorityAdminRoles() {
        return trustAuthorityAdminRoles;
    }

    public void setTrustAuthorityAdminRoles(List<String> trustAuthorityAdminRoles) {
        this.trustAuthorityAdminRoles = trustAuthorityAdminRoles;
    }

    public List<String> getStepUpSupervisorRoles() {
        return stepUpSupervisorRoles;
    }

    public void setStepUpSupervisorRoles(List<String> stepUpSupervisorRoles) {
        this.stepUpSupervisorRoles = stepUpSupervisorRoles;
    }
}
