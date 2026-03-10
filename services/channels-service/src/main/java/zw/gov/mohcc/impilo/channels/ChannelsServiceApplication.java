package zw.gov.mohcc.impilo.channels;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChannelsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChannelsServiceApplication.class, args);
    }
}
