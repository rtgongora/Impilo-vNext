package zw.gov.mohcc.impilo.docstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocumentStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentStoreApplication.class, args);
    }
}
