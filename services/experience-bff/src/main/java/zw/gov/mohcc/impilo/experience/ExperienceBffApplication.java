package zw.gov.mohcc.impilo.experience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.experience.config.BffCitizenLongtailProperties;
import zw.gov.mohcc.impilo.experience.config.BffFacilitiesProperties;
import zw.gov.mohcc.impilo.experience.config.BffProviderHubsProperties;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;
import zw.gov.mohcc.impilo.experience.config.LearningServiceRuntimeProperties;
import zw.gov.mohcc.impilo.experience.config.OrchestrationBacklogEndpoints;
import zw.gov.mohcc.impilo.experience.config.RegistryDownstreamProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    ClinicalPlatformProperties.class,
    RegistryDownstreamProperties.class,
    LearningServiceRuntimeProperties.class,
    BffCitizenLongtailProperties.class,
    BffFacilitiesProperties.class,
    BffProviderHubsProperties.class,
    OrchestrationBacklogEndpoints.class,
    zw.gov.mohcc.impilo.experience.bootstrap.BootstrapProperties.class
})
public class ExperienceBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExperienceBffApplication.class, args);
    }
}
