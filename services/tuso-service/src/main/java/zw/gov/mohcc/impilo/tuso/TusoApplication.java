package zw.gov.mohcc.impilo.tuso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(TusoProperties.class)
public class TusoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TusoApplication.class, args);
    }
}
