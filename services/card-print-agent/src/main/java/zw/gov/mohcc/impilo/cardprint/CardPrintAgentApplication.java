package zw.gov.mohcc.impilo.cardprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.cardprint.config.CardPrintProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CardPrintProperties.class)
public class CardPrintAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardPrintAgentApplication.class, args);
    }
}
