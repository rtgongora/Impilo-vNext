package zw.gov.mohcc.impilo.mushex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MushexApplication {

    public static void main(String[] args) {
        SpringApplication.run(MushexApplication.class, args);
    }
}
