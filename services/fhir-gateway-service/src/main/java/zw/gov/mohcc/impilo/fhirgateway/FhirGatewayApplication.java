package zw.gov.mohcc.impilo.fhirgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FhirGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FhirGatewayApplication.class, args);
    }
}
