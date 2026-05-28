package zw.gov.mohcc.impilo.rtc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RtcGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(RtcGatewayApplication.class, args);
    }
}
