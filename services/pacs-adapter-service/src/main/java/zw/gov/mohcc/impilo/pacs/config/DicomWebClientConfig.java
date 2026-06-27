package zw.gov.mohcc.impilo.pacs.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * RestTemplate for the external PACS DICOMweb connector (QIDO-RS / WADO-RS).
 *
 * <p>DICOMweb servers return {@code application/dicom+json}; the default Jackson converter only
 * reads {@code application/json}, so we register {@code application/dicom+json} on it.
 */
@Configuration
public class DicomWebClientConfig {

    public static final MediaType DICOM_JSON = MediaType.parseMediaType("application/dicom+json");

    @Bean(name = "dicomwebRestTemplate")
    public RestTemplate dicomwebRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
        for (HttpMessageConverter<?> converter : restTemplate.getMessageConverters()) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                List<MediaType> supported = new ArrayList<>(jackson.getSupportedMediaTypes());
                if (!supported.contains(DICOM_JSON)) {
                    supported.add(DICOM_JSON);
                    jackson.setSupportedMediaTypes(supported);
                }
            }
        }
        return restTemplate;
    }
}
