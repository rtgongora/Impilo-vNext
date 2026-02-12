package zw.gov.mohcc.impilo.companion.mock;

import org.springframework.boot.test.context.SpringBootTest;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Contract test for the mock service using the golden harness.
 *
 * This test class demonstrates how ANY Impilo service can adopt the
 * golden contract suite by:
 * 1. Extending GoldenContractSuite
 * 2. Annotating with @SpringBootTest
 * 3. Optionally overriding path methods
 *
 * Run: mvn test -pl libs/tech-companion-mock -Dtest=MockServiceContractTest
 */
@SpringBootTest(
        classes = MockServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class MockServiceContractTest extends GoldenContractSuite {

    // Uses default paths from GoldenContractSuite:
    //   /internal/v1/health
    //   /internal/v1/test-command
    //   /internal/v1/test-federation
}
