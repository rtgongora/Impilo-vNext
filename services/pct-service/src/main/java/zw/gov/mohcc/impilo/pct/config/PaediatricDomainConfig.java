package zw.gov.mohcc.impilo.pct.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthEngine;

/**
 * Exposes the paediatric domain library to Spring.
 *
 * <p>The library is deliberately framework-free so the same engine can run in a service,
 * a batch job or an offline package; that means the wiring lives here rather than as
 * annotations on the library classes. The engine reads its WHO tables once at
 * construction and is immutable and thread-safe thereafter, so a singleton is correct.</p>
 */
@Configuration
public class PaediatricDomainConfig {

    @Bean
    public GrowthEngine growthEngine(ObjectMapper objectMapper) {
        return new GrowthEngine(objectMapper);
    }
}
