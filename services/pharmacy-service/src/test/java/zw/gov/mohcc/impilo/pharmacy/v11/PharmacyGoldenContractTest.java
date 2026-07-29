package zw.gov.mohcc.impilo.pharmacy.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import zw.gov.mohcc.impilo.pharmacy.config.PharmacyTestSecurityBeans;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for Pharmacy v1.1 compliance.
 *
 * Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Tests SKIP gracefully if no v1.1 endpoints are found.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(PharmacyTestSecurityBeans.class)
public class PharmacyGoldenContractTest extends GoldenContractSuite {

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
