package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.EventOutboxRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardServiceVitoLinkTest {

    private CardEntity linkedCard(String status) {
        CardEntity c = new CardEntity();
        c.setCardId(UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setWalletId(UUID.randomUUID());
        c.setCardNumber("4000123412341234");
        c.setVitoCardNumber("VITO-CARD-1");
        c.setStatus(status);
        return c;
    }

    private CardService service(CardRepository repo) {
        return new CardService(repo, mock(EventOutboxRepository.class), new ObjectMapper());
    }

    @Test
    void freezeForVitoCard_blocks_the_linked_active_card() {
        CardRepository repo = mock(CardRepository.class);
        CardEntity card = linkedCard("ACTIVE");
        when(repo.findByVitoCardNumber("VITO-CARD-1")).thenReturn(Optional.of(card));
        when(repo.findByCardId(card.getCardId())).thenReturn(Optional.of(card));
        when(repo.save(any(CardEntity.class))).thenAnswer(i -> i.getArgument(0));

        service(repo).freezeForVitoCard("VITO-CARD-1", "VITO SMART card revoked");

        assertEquals("BLOCKED", card.getStatus());
    }

    @Test
    void freezeForVitoCard_is_noop_when_no_money_card_is_linked() {
        CardRepository repo = mock(CardRepository.class);
        when(repo.findByVitoCardNumber("VITO-CARD-X")).thenReturn(Optional.empty());

        service(repo).freezeForVitoCard("VITO-CARD-X", "revoked");

        verify(repo, never()).save(any());
    }

    @Test
    void freezeForVitoCard_is_idempotent_when_already_blocked() {
        CardRepository repo = mock(CardRepository.class);
        when(repo.findByVitoCardNumber("VITO-CARD-1")).thenReturn(Optional.of(linkedCard("BLOCKED")));

        service(repo).freezeForVitoCard("VITO-CARD-1", "revoked");

        verify(repo, never()).save(any());
    }
}
