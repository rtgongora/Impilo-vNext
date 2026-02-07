package zw.gov.mohcc.impilo.tshepo.keys;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(KeysProperties.class)
public class TshepoKeysApplication {
    public static void main(String[] args) {
        SpringApplication.run(TshepoKeysApplication.class, args);
    }
}
