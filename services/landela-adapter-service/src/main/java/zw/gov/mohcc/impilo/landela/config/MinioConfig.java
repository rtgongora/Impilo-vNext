package zw.gov.mohcc.impilo.landela.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the MinioClient bean from LandelaProperties for internal storage mode.
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(LandelaProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getInternal().getMinioEndpoint())
                .credentials(
                        properties.getInternal().getMinioAccessKey(),
                        properties.getInternal().getMinioSecretKey())
                .build();
    }
}
