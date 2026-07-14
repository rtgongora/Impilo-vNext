package zw.gov.mohcc.impilo.pipeline.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;
import zw.gov.mohcc.impilo.pipeline.config.TestSecurityConfig;

/**
 * Golden Contract integration test for data-pipeline-service v1.1 compliance.
 *
 * <p>Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Verifies header enforcement, error envelope format, idempotency,
 * and federation authority contracts.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public class DataPipelineGoldenContractTest extends GoldenContractSuite {
    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
