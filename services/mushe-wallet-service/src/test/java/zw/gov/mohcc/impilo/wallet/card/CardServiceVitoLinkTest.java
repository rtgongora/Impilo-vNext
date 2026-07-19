package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.VitoCardFreezeEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.VitoCardFreezeRepository;

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

    private VitoCardFreezeRepository freezeRepo = mock(VitoCardFreezeRepository.class);

    private CardService service(CardRepository repo) {
        return new CardService(repo, mock(EventOutboxRepository.class), new ObjectMapper(), freezeRepo);
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
    void freezeForVitoCard_records_a_tombstone_when_no_money_card_is_linked() {
        CardRepository repo = mock(CardRepository.class);
        when(repo.findByVitoCardNumber("VITO-CARD-X")).thenReturn(Optional.empty());
        when(freezeRepo.existsById("VITO-CARD-X")).thenReturn(false);

        service(repo).freezeForVitoCard("VITO-CARD-X", "revoked before link");

        // B4: revoke is not lost — a tombstone is recorded for later reconciliation.
        verify(freezeRepo).save(any(VitoCardFreezeEntity.class));
        verify(repo, never()).save(any());
    }

    @Test
    void linkVitoCard_freezes_a_card_linked_after_a_revoke_tombstone() {
        CardRepository repo = mock(CardRepository.class);
        CardEntity card = linkedCard("ACTIVE");
        card.setVitoCardNumber(null); // not yet linked
        when(repo.findByCardId(card.getCardId())).thenReturn(Optional.of(card));
        when(repo.save(any(CardEntity.class))).thenAnswer(i -> i.getArgument(0));
        VitoCardFreezeEntity tombstone = new VitoCardFreezeEntity();
        tombstone.setVitoCardNumber("VITO-CARD-1");
        tombstone.setReason("VITO SMART card revoked");
        when(freezeRepo.findById("VITO-CARD-1")).thenReturn(Optional.of(tombstone));

        service(repo).linkVitoCard(card.getCardId(), "VITO-CARD-1", card.getTenantId());

        assertEquals("BLOCKED", card.getStatus());
        verify(freezeRepo).deleteById("VITO-CARD-1"); // tombstone consumed
    }

    @Test
    void linkVitoCard_leaves_an_active_card_active_when_no_tombstone() {
        CardRepository repo = mock(CardRepository.class);
        CardEntity card = linkedCard("ACTIVE");
        card.setVitoCardNumber(null);
        when(repo.findByCardId(card.getCardId())).thenReturn(Optional.of(card));
        when(repo.save(any(CardEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(freezeRepo.findById("VITO-CARD-1")).thenReturn(Optional.empty());

        service(repo).linkVitoCard(card.getCardId(), "VITO-CARD-1", card.getTenantId());

        assertEquals("ACTIVE", card.getStatus());
    }

    @Test
    void freezeForVitoCard_is_idempotent_when_already_blocked() {
        CardRepository repo = mock(CardRepository.class);
        when(repo.findByVitoCardNumber("VITO-CARD-1")).thenReturn(Optional.of(linkedCard("BLOCKED")));

        service(repo).freezeForVitoCard("VITO-CARD-1", "revoked");

        verify(repo, never()).save(any());
    }

    @Test
    void setPhrCarry_enables_the_optin_and_stamps_consent_time() {
        CardRepository repo = mock(CardRepository.class);
        CardEntity card = linkedCard("ACTIVE");
        when(repo.findByCardId(card.getCardId())).thenReturn(Optional.of(card));
        when(repo.save(any(CardEntity.class))).thenAnswer(i -> i.getArgument(0));

        service(repo).setPhrCarry(card.getCardId(), true, card.getTenantId());

        org.junit.jupiter.api.Assertions.assertTrue(card.isPhrEnabled());
        org.junit.jupiter.api.Assertions.assertNotNull(card.getPhrConsentAt());
    }

    @Test
    void setPhrCarry_is_noop_when_state_is_unchanged() {
        CardRepository repo = mock(CardRepository.class);
        CardEntity card = linkedCard("ACTIVE"); // phrEnabled defaults to false
        when(repo.findByCardId(card.getCardId())).thenReturn(Optional.of(card));

        service(repo).setPhrCarry(card.getCardId(), false, card.getTenantId());

        verify(repo, never()).save(any());
    }
}
