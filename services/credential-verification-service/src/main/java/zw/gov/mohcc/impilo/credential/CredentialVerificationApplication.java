package zw.gov.mohcc.impilo.credential;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.credential.config.CredentialProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CredentialProperties.class)
public class CredentialVerificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CredentialVerificationApplication.class, args);
    }
}
