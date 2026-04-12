package zw.gov.mohcc.impilo.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MusheWalletApplication {
    public static void main(String[] args) {
        SpringApplication.run(MusheWalletApplication.class, args);
    }
}
