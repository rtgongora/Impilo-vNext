package zw.gov.mohcc.impilo.inventory.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.inventory.config.InventoryTestSecurityBeans;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for INVENTORY v1.1 compliance.
 *
 * Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Tests SKIP gracefully if no v1.1 endpoints are found.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(InventoryTestSecurityBeans.class)
public class InventoryGoldenContractTest extends GoldenContractSuite {

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
