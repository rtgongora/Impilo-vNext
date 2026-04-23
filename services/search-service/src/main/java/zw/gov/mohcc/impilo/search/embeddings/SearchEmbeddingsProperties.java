package zw.gov.mohcc.impilo.search.embeddings;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "impilo.search.embeddings")
public class SearchEmbeddingsProperties {

    /**
     * {@code off} — no vectors stored; semantic rank falls back to lexical proxy.
     * {@code hash} — deterministic local vectors (dev/tests, no external calls).
     * {@code http} — OpenAI-compatible {@code POST /v1/embeddings}.
     */
    private EmbeddingProvider provider = EmbeddingProvider.OFF;

    /** Vector size for {@link #provider} {@code hash}. */
    private int hashDimension = 128;

    /** Max characters of searchable text sent to the embedder. */
    private int maxInputChars = 8192;

    private final Http http = new Http();

    public EmbeddingProvider getProvider() {
        return provider;
    }

    public void setProvider(EmbeddingProvider provider) {
        this.provider = provider;
    }

    public int getHashDimension() {
        return hashDimension;
    }

    public void setHashDimension(int hashDimension) {
        this.hashDimension = hashDimension;
    }

    public int getMaxInputChars() {
        return maxInputChars;
    }

    public void setMaxInputChars(int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    public Http getHttp() {
        return http;
    }

    public static class Http {
        private String baseUrl;
        private String apiKey;
        private String path = "/v1/embeddings";
        private String model = "text-embedding-3-small";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(45);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}
