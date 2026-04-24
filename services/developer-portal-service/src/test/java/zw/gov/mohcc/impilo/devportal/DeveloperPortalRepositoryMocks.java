package zw.gov.mohcc.impilo.devportal;

import zw.gov.mohcc.impilo.devportal.domain.ApiKeyEntity;
import zw.gov.mohcc.impilo.devportal.domain.ClientEntity;
import zw.gov.mohcc.impilo.devportal.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.devportal.repository.ApiKeyRepository;
import zw.gov.mohcc.impilo.devportal.repository.ClientRepository;
import zw.gov.mohcc.impilo.devportal.repository.OutboxEventRepository;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mockito stubs that mimic JPA {@code @GeneratedValue} behaviour for unit tests.
 */
public final class DeveloperPortalRepositoryMocks {

    private DeveloperPortalRepositoryMocks() {}

    public static void stubPersistence(
            ClientRepository clientRepo,
            ApiKeyRepository keyRepo,
            OutboxEventRepository outboxRepo) {

        when(clientRepo.save(any(ClientEntity.class))).thenAnswer(inv -> {
            ClientEntity c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });
        when(keyRepo.save(any(ApiKeyEntity.class))).thenAnswer(inv -> {
            ApiKeyEntity k = inv.getArgument(0);
            if (k.getId() == null) {
                k.setId(UUID.randomUUID());
            }
            return k;
        });
        when(outboxRepo.save(any(OutboxEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}
