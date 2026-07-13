package zw.gov.mohcc.impilo.wallet.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.wallet.card.CardService;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VitoCardEventConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void revoked_event_freezes_the_linked_money_card() {
        CardService cardService = mock(CardService.class);
        VitoCardEventConsumer consumer = new VitoCardEventConsumer(cardService, mapper);

        consumer.onVitoCardEvent(
                "{\"event_type\":\"CARD_REVOKED\",\"payload\":{\"cardNumber\":\"VITO-CARD-1\",\"status\":\"REVOKED\"}}");

        verify(cardService).freezeForVitoCard(eq("VITO-CARD-1"), contains("revoked"));
    }

    @Test
    void suspended_event_freezes_the_money_card() {
        CardService cardService = mock(CardService.class);
        VitoCardEventConsumer consumer = new VitoCardEventConsumer(cardService, mapper);

        consumer.onVitoCardEvent(
                "{\"eventType\":\"CARD_SUSPENDED\",\"payload\":{\"cardNumber\":\"VITO-CARD-2\"}}");

        verify(cardService).freezeForVitoCard(eq("VITO-CARD-2"), contains("suspended"));
    }

    @Test
    void non_revocation_events_do_not_freeze() {
        CardService cardService = mock(CardService.class);
        VitoCardEventConsumer consumer = new VitoCardEventConsumer(cardService, mapper);

        consumer.onVitoCardEvent(
                "{\"event_type\":\"CARD_ACTIVATED\",\"payload\":{\"cardNumber\":\"VITO-CARD-3\"}}");

        verify(cardService, never()).freezeForVitoCard(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void poison_message_does_not_throw() {
        CardService cardService = mock(CardService.class);
        VitoCardEventConsumer consumer = new VitoCardEventConsumer(cardService, mapper);
        consumer.onVitoCardEvent("not json");
        verify(cardService, never()).freezeForVitoCard(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
