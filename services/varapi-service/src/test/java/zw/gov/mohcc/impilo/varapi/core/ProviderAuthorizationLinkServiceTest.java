package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAuthorizationLinkEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderAuthorizationLinkRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-P1: every bind/rebind supersedes the previous ACTIVE link and inserts a new
 * ACTIVE row — binding history is never overwritten, and a binding without a
 * person anchor is rejected.
 */
@ExtendWith(MockitoExtension.class)
class ProviderAuthorizationLinkServiceTest {

    @Mock private ProviderAuthorizationLinkRepository linkRepository;

    private ProviderAuthorizationLinkService service;

    private final UUID tenantId = UUID.randomUUID();
    private ProviderEntity provider;

    @BeforeEach
    void setUp() {
        service = new ProviderAuthorizationLinkService(linkRepository);
        provider = new ProviderEntity();
        provider.setId(42L);
        provider.setTenantId(tenantId);
        provider.setProviderPublicId("PROV42PUBLICID");
    }

    @Test
    void recordBinding_supersedesThenInsertsActiveLink() {
        UUID healthId = UUID.randomUUID();
        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProviderAuthorizationLinkEntity link = service.recordBinding(
                tenantId, provider, healthId,
                ProviderAuthorizationLinkEntity.TYPE_CLAIM,
                "claim-token:7", "RECORD_LINKED", "BOOTSTRAP_CLAIM", "actor-1");

        InOrder order = inOrder(linkRepository);
        order.verify(linkRepository).supersedeActiveForProvider(eq(tenantId), eq(42L), any(Instant.class));
        ArgumentCaptor<ProviderAuthorizationLinkEntity> captor =
                ArgumentCaptor.forClass(ProviderAuthorizationLinkEntity.class);
        order.verify(linkRepository).save(captor.capture());

        ProviderAuthorizationLinkEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ProviderAuthorizationLinkEntity.STATUS_ACTIVE);
        assertThat(saved.getLinkType()).isEqualTo(ProviderAuthorizationLinkEntity.TYPE_CLAIM);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getProviderId()).isEqualTo(42L);
        assertThat(saved.getProviderPublicId()).isEqualTo("PROV42PUBLICID");
        assertThat(saved.getImpiloHealthId()).isEqualTo(healthId);
        assertThat(saved.getEvidenceRef()).isEqualTo("claim-token:7");
        assertThat(saved.getAssuranceOutcome()).isEqualTo("RECORD_LINKED");
        assertThat(saved.getProofingChannel()).isEqualTo("BOOTSTRAP_CLAIM");
        assertThat(saved.getActorRef()).isEqualTo("actor-1");
        assertThat(link).isSameAs(saved);
    }

    @Test
    void recordBinding_rejectsMissingHealthId() {
        assertThatThrownBy(() -> service.recordBinding(
                tenantId, provider, null,
                ProviderAuthorizationLinkEntity.TYPE_RECOVERY,
                "recovery-token:1", "RECORD_LINKED", "RECOVERY", "actor-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(linkRepository, never()).supersedeActiveForProvider(any(), any(), any());
        verify(linkRepository, never()).save(any());
    }
}
