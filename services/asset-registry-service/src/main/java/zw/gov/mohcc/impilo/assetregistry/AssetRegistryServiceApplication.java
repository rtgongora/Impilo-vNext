package zw.gov.mohcc.impilo.assetregistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AssetRegistryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssetRegistryServiceApplication.class, args);
    }
}
