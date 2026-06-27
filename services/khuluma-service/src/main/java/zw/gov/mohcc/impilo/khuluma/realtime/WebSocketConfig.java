package zw.gov.mohcc.impilo.khuluma.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the Khuluma realtime WebSocket endpoint. Origins are open at this layer because the
 * gateway sits behind Envoy/the BFF, which enforce trust; per-connection identity is established
 * by {@link RealtimeHandshakeInterceptor}.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final KhulumaWebSocketHandler handler;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(KhulumaWebSocketHandler handler, RealtimeHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/internal/v1/khuluma/stream/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
