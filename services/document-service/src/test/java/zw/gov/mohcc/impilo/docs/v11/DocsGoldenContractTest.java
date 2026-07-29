package zw.gov.mohcc.impilo.docs.v11;

import org.springframework.boot.test.context.SpringBootTest;
import zw.gov.mohcc.impilo.docstore.DocumentStoreApplication;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for DOCUMENT v1.1 compliance.
 *
 * Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Tests SKIP gracefully if no v1.1 endpoints are found.
 */
@SpringBootTest(classes = DocumentStoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class DocsGoldenContractTest extends GoldenContractSuite {
    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
