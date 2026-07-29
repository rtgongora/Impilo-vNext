package zw.gov.mohcc.impilo.ndr.v11;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;
import zw.gov.mohcc.impilo.ndr.config.NdrTestSecurityBeans;

/**
 * Golden Contract integration test for NDR v1.1 compliance.
 *
 * <p>Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Verifies header enforcement, error envelope format, and idempotency
 * for all /internal/v1/ endpoints.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(NdrTestSecurityBeans.class)
public class NdrGoldenContractTest extends GoldenContractSuite {

    @MockBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, String> kafkaTemplate;

    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
