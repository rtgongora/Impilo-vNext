package zw.gov.mohcc.impilo.docstore.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO client configuration.
 *
 * Creates a singleton MinioClient bean configured from DocumentStoreProperties.
 * The client is used by ObjectStorageService for all S3-compatible operations:
 * put, get, presigned URL generation, and deletion.
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(DocumentStoreProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getMinio().getEndpoint())
                .credentials(
                        properties.getMinio().getAccessKey(),
                        properties.getMinio().getSecretKey()
                )
                .build();
    }
}
