package zw.gov.mohcc.impilo.air;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AiModelRegistryApplicationTest {

    @Test
    void contextLoads() {
        // smoke: wiring + security (OAuth off in test profile)
    }
}
