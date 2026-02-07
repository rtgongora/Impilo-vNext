package zw.gov.mohcc.impilo.tshepo.sdk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.AccessMode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustContextHolderTest {

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void set_andGet_returnsContext() {
        TrustContext ctx = buildContext();

        TrustContextHolder.set(ctx);

        assertThat(TrustContextHolder.get()).isSameAs(ctx);
    }

    @Test
    void clear_removesContext() {
        TrustContextHolder.set(buildContext());

        TrustContextHolder.clear();

        assertThat(TrustContextHolder.get()).isNull();
    }

    @Test
    void require_withoutContext_throws() {
        assertThatThrownBy(TrustContextHolder::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TrustContext not set");
    }

    private TrustContext buildContext() {
        return new TrustContext(
                UUID.randomUUID(), "actor-1", "PROVIDER", "TREATMENT",
                "fp-abc123", UUID.randomUUID(),
                null, null, null, AccessMode.EXTERNAL
        );
    }
}
