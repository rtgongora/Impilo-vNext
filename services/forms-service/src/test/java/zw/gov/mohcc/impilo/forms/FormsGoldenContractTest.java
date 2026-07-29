package zw.gov.mohcc.impilo.forms;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class FormsGoldenContractTest extends GoldenContractSuite {
    // All v1.1 contract tests inherited from GoldenContractSuite.
    // Endpoints auto-discovered via RequestMappingHandlerMapping.
}
