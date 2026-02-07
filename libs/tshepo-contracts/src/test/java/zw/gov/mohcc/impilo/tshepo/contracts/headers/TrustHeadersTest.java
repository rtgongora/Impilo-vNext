package zw.gov.mohcc.impilo.tshepo.contracts.headers;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustHeadersTest {

    @Test
    void mandatoryHeaders_contains6Headers() {
        assertThat(TrustHeaders.MANDATORY).hasSize(6);
        assertThat(TrustHeaders.MANDATORY).containsExactly(
                TrustHeaders.TENANT_ID,
                TrustHeaders.ACTOR_ID,
                TrustHeaders.ACTOR_TYPE,
                TrustHeaders.PURPOSE_OF_USE,
                TrustHeaders.DEVICE_FINGERPRINT,
                TrustHeaders.CORRELATION_ID
        );
    }

    @Test
    void headerValues_areLowerCaseXPrefixed() throws IllegalAccessException {
        List<String> headerValues = getAllStringConstants();

        assertThat(headerValues).isNotEmpty();
        for (String header : headerValues) {
            assertThat(header)
                    .as("Header '%s' should start with 'x-'", header)
                    .startsWith("x-");
            assertThat(header)
                    .as("Header '%s' should be lower-case", header)
                    .isEqualTo(header.toLowerCase());
        }
    }

    /**
     * Uses reflection to gather all public static final String fields
     * from TrustHeaders (excluding the MANDATORY array).
     */
    private List<String> getAllStringConstants() throws IllegalAccessException {
        List<String> values = new ArrayList<>();
        for (Field field : TrustHeaders.class.getDeclaredFields()) {
            if (field.getType() == String.class
                    && java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                values.add((String) field.get(null));
            }
        }
        return values;
    }
}
