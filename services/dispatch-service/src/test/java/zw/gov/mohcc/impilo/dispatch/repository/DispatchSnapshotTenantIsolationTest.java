package zw.gov.mohcc.impilo.dispatch.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.dispatch.domain.DispatchJobEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the D2 fix (G014): the dispatch snapshot query is tenant-scoped. Before the fix
 * {@code findSnapshotAsOf} had no tenant predicate, so a snapshot returned every tenant's
 * jobs (a cross-tenant leak). A JPA slice test — no security context needed.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DispatchSnapshotTenantIsolationTest {

    @Autowired private DispatchJobRepository repository;

    @Test
    void snapshotReturnsOnlyTheRequestingTenantsJobs() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        repository.save(new DispatchJobEntity(UUID.randomUUID(), tenantA, "FAC-A1"));
        repository.save(new DispatchJobEntity(UUID.randomUUID(), tenantA, "FAC-A2"));
        repository.save(new DispatchJobEntity(UUID.randomUUID(), tenantB, "FAC-B1"));

        OffsetDateTime asOf = OffsetDateTime.now().plusDays(1);

        Page<DispatchJobEntity> snapshotA = repository.findSnapshotAsOf(tenantA, asOf, PageRequest.of(0, 100));
        assertThat(snapshotA.getContent())
                .hasSize(2)
                .allMatch(j -> j.getTenantId().equals(tenantA));

        // Tenant B's snapshot does NOT include tenant A's jobs — the leak is closed.
        Page<DispatchJobEntity> snapshotB = repository.findSnapshotAsOf(tenantB, asOf, PageRequest.of(0, 100));
        assertThat(snapshotB.getContent())
                .hasSize(1)
                .allMatch(j -> j.getTenantId().equals(tenantB));
    }
}
