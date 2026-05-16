package zw.gov.mohcc.impilo.llm.core;

import java.util.Set;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmProviderHealthStatus;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmResponse;

public interface LlmProvider {
    String name();
    boolean enabled();
    String model();
    int priority();
    Set<LlmCapability> capabilities();
    LlmResponse invoke(LlmRequest request);
    LlmProviderHealthStatus healthStatus();
}
