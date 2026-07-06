package zw.gov.mohcc.impilo.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OrgMirrorProperties.class)
public class WorkforceGovernanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkforceGovernanceApplication.class, args);
    }
}
