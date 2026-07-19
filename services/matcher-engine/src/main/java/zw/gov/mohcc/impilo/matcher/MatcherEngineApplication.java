package zw.gov.mohcc.impilo.matcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Biometric Matching Engine — core-infrastructure compute appliance.
 *
 * <p>Deployed in the estate infra lane (like Orthanc/LiveKit), reached only by the
 * abis-service adapter over the cluster network. Stateless: it never stores a
 * template — ABIS holds encrypted custody and supplies templates per request.</p>
 */
@SpringBootApplication
public class MatcherEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(MatcherEngineApplication.class, args);
    }
}
