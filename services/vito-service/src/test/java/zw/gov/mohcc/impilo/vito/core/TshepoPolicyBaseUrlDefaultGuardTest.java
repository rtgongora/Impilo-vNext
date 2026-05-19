package zw.gov.mohcc.impilo.vito.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TshepoPolicyBaseUrlDefaultGuardTest {

    @Test
    void runtimeDefaultsMustNotPointPolicyClientsToLegacy8079() throws Exception {
        String text = Files.readString(Path.of("src", "main", "resources", "application.yml"));
        assertThat(text).contains("TSHEPO_AUTHZ_BASE_URL");
        assertThat(text).doesNotContain("TSHEPO_POLICY_BASE_URL:http://localhost:8079");
    }
}
