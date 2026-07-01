package zw.gov.mohcc.impilo.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "learning")
public class LearningProperties {

    private final Security security = new Security();
    private final Fundo fundo = new Fundo();
    private final Integration integration = new Integration();
    private final Moodle moodle = new Moodle();
    private final Events events = new Events();

    public Security getSecurity() {
        return security;
    }

    public Fundo getFundo() {
        return fundo;
    }

    public Integration getIntegration() {
        return integration;
    }

    public Moodle getMoodle() {
        return moodle;
    }

    public Events getEvents() {
        return events;
    }

    public static class Security {
        private boolean oauth2Enabled = true;

        public boolean isOauth2Enabled() {
            return oauth2Enabled;
        }

        public void setOauth2Enabled(boolean oauth2Enabled) {
            this.oauth2Enabled = oauth2Enabled;
        }
    }

    public static class Fundo {
        private String publicBaseUrl = "https://fundo.impilo.local";
        /**
         * When true, {@code deepLink} / Fundo launch URLs point at Moodle login with
         * {@code wantsurl} set to the target course view URL (post-login redirect).
         */
        private boolean wantsurlLoginEnabled = false;
        /** Moodle web-root relative login script, e.g. {@code /login/index.php}. */
        private String loginPath = "/login/index.php";
        /**
         * {@code DIRECT} — course view URL; {@code WANTSURL} — login + wantsurl; {@code TEMPLATE} —
         * {@link #launchUrlTemplate} with placeholders (OIDC-style authorize URLs supported).
         */
        private String launchMode = "DIRECT";
        /**
         * When {@code launchMode=TEMPLATE}, used as launch URL. Placeholders: {@code {courseViewUrl}},
         * {@code {encodedCourseViewUrl}}, {@code {courseViewPath}} (path+query only).
         */
        private String launchUrlTemplate = "";

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public boolean isWantsurlLoginEnabled() {
            return wantsurlLoginEnabled;
        }

        public void setWantsurlLoginEnabled(boolean wantsurlLoginEnabled) {
            this.wantsurlLoginEnabled = wantsurlLoginEnabled;
        }

        public String getLoginPath() {
            return loginPath;
        }

        public void setLoginPath(String loginPath) {
            this.loginPath = loginPath;
        }

        public String getLaunchMode() {
            return launchMode;
        }

        public void setLaunchMode(String launchMode) {
            this.launchMode = launchMode;
        }

        public String getLaunchUrlTemplate() {
            return launchUrlTemplate;
        }

        public void setLaunchUrlTemplate(String launchUrlTemplate) {
            this.launchUrlTemplate = launchUrlTemplate;
        }
    }

    public static class Integration {
        private String varapiInternalKey = "";
        /** Shared secret for Tshepo/Varapi orchestration calls (assign path, prerequisites, Moodle probes). */
        private String orchestrationInternalKey = "";

        public String getVarapiInternalKey() {
            return varapiInternalKey;
        }

        public void setVarapiInternalKey(String varapiInternalKey) {
            this.varapiInternalKey = varapiInternalKey;
        }

        public String getOrchestrationInternalKey() {
            return orchestrationInternalKey;
        }

        public void setOrchestrationInternalKey(String orchestrationInternalKey) {
            this.orchestrationInternalKey = orchestrationInternalKey;
        }
    }

    /**
     * Phase 6E — controlled retirement of the seven legacy event-type
     * strings that {@code LearningPlatformFacade.appendOutboxPair(...)}
     * has been dual-emitting since Phase 4B.
     *
     * <p>When {@code legacyEmissionEnabled=true} (the default), both the
     * legacy and v1.1-compliant event-type rows are written to
     * {@code lrn_event_outbox} inside the same transaction — fully
     * backwards-compatible. When {@code legacyEmissionEnabled=false}, only
     * the v1.1 row is written. The flag exists so operators can switch a
     * single tenant or environment to v1.1-only emission once every
     * downstream consumer (Varapi, credential-verification, etc.) has
     * confirmed migration, without re-deploying the service or removing
     * the legacy constants from the codebase.</p>
     *
     * <p>No event-type strings are renamed or deleted by this flag — the
     * legacy constants remain in the codebase and can be re-enabled
     * instantly by flipping the flag back to {@code true}.</p>
     */
    public static class Events {
        private boolean legacyEmissionEnabled = true;

        public boolean isLegacyEmissionEnabled() {
            return legacyEmissionEnabled;
        }

        public void setLegacyEmissionEnabled(boolean legacyEmissionEnabled) {
            this.legacyEmissionEnabled = legacyEmissionEnabled;
        }
    }

    public static class Moodle {
        /** Moodle mobile / service user token with appropriate capabilities. */
        private String wsToken = "";
        /**
         * Full REST endpoint URL (…/webservice/rest/server.php). When blank, derived from
         * {@code Fundo.publicBaseUrl} + {@code /webservice/rest/server.php}.
         */
        private String wsEndpointUrl = "";

        public String getWsToken() {
            return wsToken;
        }

        public void setWsToken(String wsToken) {
            this.wsToken = wsToken;
        }

        public String getWsEndpointUrl() {
            return wsEndpointUrl;
        }

        public void setWsEndpointUrl(String wsEndpointUrl) {
            this.wsEndpointUrl = wsEndpointUrl;
        }
    }
}
