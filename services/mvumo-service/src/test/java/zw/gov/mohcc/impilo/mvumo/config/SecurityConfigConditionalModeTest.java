package zw.gov.mohcc.impilo.mvumo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigConditionalModeTest {

    @Test
    void productionSecurityChainIsDefaultAndTestChainRequiresExplicitTestProperty() throws Exception {
        Method testChain = SecurityConfig.class.getDeclaredMethod(
                "testChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                zw.gov.mohcc.impilo.shared.auth.TrustContextFilter.class);
        Method productionChain = SecurityConfig.class.getDeclaredMethod(
                "productionChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                zw.gov.mohcc.impilo.shared.auth.TrustContextFilter.class);

        ConditionalOnProperty testCondition = testChain.getAnnotation(ConditionalOnProperty.class);
        ConditionalOnProperty productionCondition = productionChain.getAnnotation(ConditionalOnProperty.class);

        assertThat(testCondition).isNotNull();
        assertThat(testCondition.name()).containsExactly("impilo.security.disable-oauth-for-tests");
        assertThat(testCondition.havingValue()).isEqualTo("true");

        assertThat(productionCondition).isNotNull();
        assertThat(productionCondition.name()).containsExactly("impilo.security.disable-oauth-for-tests");
        assertThat(productionCondition.havingValue()).isEqualTo("false");
        assertThat(productionCondition.matchIfMissing()).isTrue();
    }
}
