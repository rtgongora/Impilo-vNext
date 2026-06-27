package zw.gov.mohcc.impilo.khuluma.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import zw.gov.mohcc.impilo.khuluma.core.ActorContext;
import zw.gov.mohcc.impilo.khuluma.core.ConversationService;
import zw.gov.mohcc.impilo.khuluma.core.MessageService;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end realtime proof over BOTH transports against a running server: a sender's
 * {@code MessageService.send} reaches a subscribed peer live, as a WebSocket frame and as an SSE
 * event. The single subscription/dispatch core ({@link RealtimeHub}) backs both. (Redis fan-out is
 * disabled in the test profile; a single instance delivers locally — its multi-instance logic is
 * covered by {@link RealtimeFanoutTest}.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealtimeGatewayTest {

    @LocalServerPort private int port;
    @Autowired private ConversationService conversationService;
    @Autowired private MessageService messageService;

    private static ActorContext ctx(UUID tenant, String actor) {
        return new ActorContext(tenant, "national-spine", actor, "PROVIDER", UUID.randomUUID().toString());
    }

    private ConversationEntity directConversation(ActorContext owner, String peer) {
        return conversationService.create(owner, "DIRECT", null, null,
                List.of(new ConversationService.NewParticipant(peer, "PROVIDER", "Peer", "MEMBER")), null);
    }

    @Test
    void websocketSubscriber_receivesLiveMessage() throws Exception {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-A");
        ConversationEntity conv = directConversation(a, "provider-B");

        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("X-Tenant-ID", tenant.toString());
        headers.add("X-Pod-ID", "national");
        headers.add("X-Request-ID", UUID.randomUUID().toString());
        headers.add("X-Correlation-ID", UUID.randomUUID().toString());
        headers.add("X-Actor-ID", "provider-B");
        headers.add("X-Actor-Type", "PROVIDER");

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                frames.add(message.getPayload());
            }
        }, headers, URI.create("ws://localhost:" + port + "/internal/v1/khuluma/stream/ws")).get(5, TimeUnit.SECONDS);

        assertThat(pollFor(frames, "stream.ready")).contains("stream.ready");

        messageService.send(a, conv.getConversationId(), "Hello over WS", "TEXT", "w1", null);

        String event = pollFor(frames, "message.created");
        assertThat(event).contains("message.created").contains("Hello over WS");
        session.close();
    }

    @Test
    void sseSubscriber_receivesLiveMessage() throws Exception {
        UUID tenant = UUID.randomUUID();
        ActorContext a = ctx(tenant, "provider-C");
        ConversationEntity conv = directConversation(a, "provider-D");

        HttpClient http = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/internal/v1/khuluma/stream/sse"))
                .header("X-Tenant-ID", tenant.toString())
                .header("X-Pod-ID", "national")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "provider-D")
                .header("X-Actor-Type", "PROVIDER")
                .header("Accept", "text/event-stream")
                .GET().build();

        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            } catch (Exception ignored) {
                // stream closed
            }
        });
        reader.setDaemon(true);
        reader.start();

        assertThat(pollFor(lines, "stream.ready")).isNotNull();

        messageService.send(a, conv.getConversationId(), "Hello over SSE", "TEXT", "s1", null);

        String event = pollFor(lines, "Hello over SSE");
        assertThat(event).isNotNull();
        reader.interrupt();
    }

    /** Poll a queue until an entry contains the marker (or timeout); returns it or null. */
    private static String pollFor(BlockingQueue<String> queue, String marker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            String value = queue.poll(200, TimeUnit.MILLISECONDS);
            if (value != null && value.contains(marker)) {
                return value;
            }
        }
        return null;
    }
}
