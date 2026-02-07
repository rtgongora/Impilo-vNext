package zw.gov.mohcc.impilo.tshepo.authz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;

/**
 * TSHEPO Authorization & Policy Enforcement Service.
 *
 * <p>This is the brain of the Impilo trust layer. Envoy calls this service
 * on every inbound request via ext_authz (gRPC on port 9090, HTTP on port 8081).
 * It performs ABAC/RBAC policy evaluation, purpose-of-use enforcement,
 * break-glass logic, device risk scoring, session assurance, and step-up
 * authentication triggers.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AuthzProperties.class)
public class TshepoAuthzApplication {

    public static void main(String[] args) {
        SpringApplication.run(TshepoAuthzApplication.class, args);
    }
}
