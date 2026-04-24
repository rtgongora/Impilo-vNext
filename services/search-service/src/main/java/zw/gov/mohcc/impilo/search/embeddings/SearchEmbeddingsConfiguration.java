package zw.gov.mohcc.impilo.search.embeddings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({SearchEmbeddingsProperties.class, SearchRankingProperties.class})
public class SearchEmbeddingsConfiguration {

    @Bean
    EmbeddingModel embeddingModel(SearchEmbeddingsProperties embeddings,
                                    ObjectMapper objectMapper) {
        return switch (embeddings.getProvider()) {
            case OFF -> text -> null;
            case HASH -> new HashEmbeddingModel(embeddings.getHashDimension());
            case HTTP -> buildHttp(embeddings, objectMapper);
        };
    }

    private static EmbeddingModel buildHttp(SearchEmbeddingsProperties embeddings, ObjectMapper objectMapper) {
        String base = embeddings.getHttp().getBaseUrl();
        String key = embeddings.getHttp().getApiKey();
        if (base == null || base.isBlank() || key == null || key.isBlank()) {
            return text -> null;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        Duration connect = embeddings.getHttp().getConnectTimeout();
        Duration read = embeddings.getHttp().getReadTimeout();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connect != null ? connect : Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(httpClient);
        rf.setReadTimeout(read != null ? read : Duration.ofSeconds(45));
        RestClient client = RestClient.builder()
                .baseUrl(trimmed)
                .requestFactory(rf)
                .defaultHeader("Authorization", "Bearer " + key)
                .build();
        return new OpenAiHttpEmbeddingModel(
                client,
                embeddings.getHttp().getPath(),
                embeddings.getHttp().getModel(),
                objectMapper);
    }
}
